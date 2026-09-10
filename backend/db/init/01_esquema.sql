-- Esquema del padrón EPS con tres roles y registro offline.
--
-- Postgres ejecuta este archivo automáticamente la primera vez que se crea el
-- volumen de datos (docker-entrypoint-initdb.d). Sobre un volumen que ya tiene
-- datos NO corre: hay que recrearlo.
--
-- Reemplaza al esquema anterior, donde `personas` era además la tabla de
-- autenticación. Esa mezcla era el defecto de raíz del sistema: una persona
-- del padrón no es una cuenta del sistema, y tratarlas como lo mismo hacía
-- imposible que un registrador diera de alta a un tercero.

BEGIN;

-- ===========================================================================
-- usuarios — cuentas del sistema. Solo admin y registradores.
--
-- Las personas del padrón NO viven acá: no tienen credenciales. Entran con su
-- tipo y número de documento, que se validan contra `personas`.
-- ===========================================================================
CREATE TABLE usuarios (
    id            uuid         PRIMARY KEY,
    usuario       varchar(60)  NOT NULL,
    password_hash varchar(255) NOT NULL,
    rol           varchar(20)  NOT NULL CHECK (rol IN ('admin', 'registrador')),
    activo        boolean      NOT NULL DEFAULT true,

    -- Quién creó esta cuenta. Es NULL solo para el primer admin, que nace del
    -- script de arranque y por lo tanto no tiene autor dentro del sistema.
    creado_por    uuid         REFERENCES usuarios(id),
    creado_en     timestamptz  NOT NULL DEFAULT now()
);

-- Sin distinguir mayúsculas. Para una persona "Ana" y "ana" son la misma
-- cuenta; para la base serían dos. Ese desacuerdo termina en un login que
-- falla sin que nadie entienda por qué.
CREATE UNIQUE INDEX idx_usuarios_usuario ON usuarios (lower(usuario));

-- El admin lista registradores activos con frecuencia.
CREATE INDEX idx_usuarios_rol ON usuarios (rol) WHERE activo;

-- ===========================================================================
-- personas — el padrón.
-- ===========================================================================

-- Cursor de sincronización. Se usa una secuencia y no `updated_at` porque
-- now() se fija al inicio de la transacción: una transacción que confirma
-- tarde queda por debajo de un cursor ya avanzado, y ese cambio no se
-- descarga nunca. La secuencia además da un orden total estable para paginar.
CREATE SEQUENCE personas_cambio_seq;

CREATE TABLE personas (
    id               uuid        PRIMARY KEY,

    tipo_documento   varchar(5)  NOT NULL
                     CHECK (tipo_documento IN ('CC','TI','RC','CE','PPT','PEP')),
    numero_documento varchar(20) NOT NULL,

    primer_nombre    varchar(60) NOT NULL,
    segundo_nombre   varchar(60),
    primer_apellido  varchar(60) NOT NULL,
    segundo_apellido varchar(60),

    fecha_nacimiento date        NOT NULL,
    sexo             varchar(1)  NOT NULL CHECK (sexo IN ('M','F','I')),
    telefono         varchar(20),

    -- Quién la registró. Responde el requisito del admin y además delimita
    -- qué puede editar cada registrador.
    creado_por       uuid        NOT NULL REFERENCES usuarios(id),

    -- Bloqueo optimista. La edición compara contra este valor y rechaza con
    -- 409 si no coincide.
    version          integer     NOT NULL DEFAULT 1,

    cambio_seq       bigint      NOT NULL DEFAULT nextval('personas_cambio_seq'),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz
);

-- El índice único va sobre el PAR, no sobre el número solo: una CC y una TI
-- pueden compartir número y son personas distintas. El esquema anterior
-- indexaba solo el número y rechazaba altas legítimas.
--
-- Es parcial: dar de baja un registro libera ese documento.
CREATE UNIQUE INDEX idx_personas_documento_activo
    ON personas (tipo_documento, numero_documento)
    WHERE deleted_at IS NULL;

-- La sincronización pagina por esta columna.
CREATE UNIQUE INDEX idx_personas_cambio_seq ON personas (cambio_seq);

-- El registrador solo descarga lo suyo. Sin este índice, ese filtro recorre
-- la tabla entera en cada sincronización de cada dispositivo.
CREATE INDEX idx_personas_creado_por ON personas (creado_por);

-- El cursor de sincronización se mantiene por trigger y no en cada UPDATE a
-- mano. Es un invariante, no una decisión por consulta: un UPDATE que olvide
-- avanzarlo deja ese cambio invisible para todos los dispositivos, y falla en
-- silencio. Acá no se puede olvidar.
CREATE OR REPLACE FUNCTION personas_marcar_cambio() RETURNS trigger AS $$
BEGIN
    NEW.cambio_seq := nextval('personas_cambio_seq');
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_personas_marcar_cambio
    BEFORE UPDATE ON personas
    FOR EACH ROW EXECUTE FUNCTION personas_marcar_cambio();

-- `version` a propósito NO se toca en el trigger. Es lógica de negocio del
-- bloqueo optimista y la controla el controlador; el cursor es mecánico.

-- ===========================================================================
-- conflictos — acá se guardan los datos que el sistema anterior descartaba.
--
-- El caso grave es el alta duplicada: dos registradores dan de alta a la
-- misma persona sin conexión, cada teléfono genera su propio UUID, y el
-- segundo choca contra el índice único. El sistema anterior lo marcaba en el
-- celular y lo borraba de la cola: el dato nunca salía del dispositivo.
-- ===========================================================================
CREATE TABLE conflictos (
    id                   uuid        PRIMARY KEY,

    tipo                 varchar(24) NOT NULL
                         CHECK (tipo IN ('ALTA_DUPLICADA','EDICION_CONCURRENTE')),

    -- Contra qué registro ya existente chocó.
    persona_existente_id uuid        NOT NULL REFERENCES personas(id),

    -- El UUID que había generado el dispositivo perdedor. Sirve para que ese
    -- teléfono reconozca su propio registro cuando vuelva a sincronizar.
    persona_id_cliente   uuid,

    -- El intento completo, tal como lo mandó el dispositivo.
    datos_enviados       jsonb       NOT NULL,

    -- Solo en EDICION_CONCURRENTE: contra qué versión se editó.
    version_base         integer,

    enviado_por          uuid        NOT NULL REFERENCES usuarios(id),
    enviado_en           timestamptz NOT NULL DEFAULT now(),

    estado               varchar(12) NOT NULL DEFAULT 'PENDIENTE'
                         CHECK (estado IN ('PENDIENTE','RESUELTO','DESCARTADO')),
    resuelto_por         uuid        REFERENCES usuarios(id),
    resuelto_en          timestamptz,
    nota_resolucion      text,

    -- Un conflicto resuelto sin quién ni cuándo no es auditable.
    CONSTRAINT resolucion_completa CHECK (
        (estado = 'PENDIENTE'  AND resuelto_por IS NULL AND resuelto_en IS NULL) OR
        (estado <> 'PENDIENTE' AND resuelto_por IS NOT NULL AND resuelto_en IS NOT NULL)
    )
);

-- La cola del admin: solo pendientes, más viejos primero.
CREATE INDEX idx_conflictos_pendientes
    ON conflictos (enviado_en) WHERE estado = 'PENDIENTE';

-- Al sincronizar, el dispositivo pregunta por sus propios conflictos.
CREATE INDEX idx_conflictos_cliente ON conflictos (persona_id_cliente);

-- ===========================================================================
-- historial_cambios — auditoría append-only, UNA FILA POR CAMPO.
--
-- La granularidad por campo no es un detalle de forma: es lo que permite que
-- el merge automático de ediciones y la fusión manual del admin salgan de la
-- misma consulta.
--
-- Nunca se borra nada. El esquema anterior recortaba a los últimos 3 cambios,
-- lo que convertía la auditoría en una ventana rodante inútil para una EPS.
-- ===========================================================================
CREATE TABLE historial_cambios (
    id             bigserial   PRIMARY KEY,

    -- Sin ON DELETE CASCADE, y es deliberado: la auditoría tiene que
    -- sobrevivir al registro. Las bajas son lógicas (deleted_at), así que
    -- esto solo impide un borrado físico, que es exactamente lo que se busca.
    persona_id     uuid        NOT NULL REFERENCES personas(id),

    -- Versión RESULTANTE del cambio. El merge consulta por rango sobre esto.
    version        integer     NOT NULL,

    operacion      varchar(10) NOT NULL
                   CHECK (operacion IN ('CREATE','UPDATE','DELETE')),

    -- NULL en CREATE y DELETE: esas operaciones son sobre el registro entero,
    -- no sobre un campo.
    campo          varchar(40),
    valor_anterior text,
    valor_nuevo    text,

    realizado_por  uuid        NOT NULL REFERENCES usuarios(id),
    realizado_en   timestamptz NOT NULL DEFAULT now(),

    -- De qué conflicto salió este cambio, si salió de uno.
    --
    -- Sin esta columna la auditoría dice "el admin cambió el teléfono" pero no
    -- por qué, y para reconstruirlo habría que correlacionar por hora, que es
    -- frágil. Con ella, cada cambio nacido de una resolución queda atado al
    -- intento original y a quién lo había enviado.
    conflicto_id   uuid        REFERENCES conflictos(id),

    -- Un cambio de campo sin campo, o un alta con campo, indican un bug en el
    -- controlador. Mejor que la base lo rechace a que quede auditoría falsa.
    CONSTRAINT campo_coherente_con_operacion CHECK (
        (operacion = 'UPDATE' AND campo IS NOT NULL) OR
        (operacion IN ('CREATE','DELETE') AND campo IS NULL)
    )
);

-- Lectura del historial de una persona, en orden.
CREATE INDEX idx_historial_persona ON historial_cambios (persona_id, version);

-- La consulta del merge: qué campos se tocaron desde cierta versión.
CREATE INDEX idx_historial_merge ON historial_cambios (persona_id, campo, version);

-- El admin puede auditar por autor.
CREATE INDEX idx_historial_autor ON historial_cambios (realizado_por, realizado_en);


-- Qué cambios salieron de resolver conflictos.
CREATE INDEX idx_historial_conflicto
    ON historial_cambios (conflicto_id) WHERE conflicto_id IS NOT NULL;

COMMIT;
