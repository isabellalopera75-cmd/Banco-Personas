# Diseño: padrón EPS con tres roles y registro offline

Documento de arquitectura para la reconstrucción del sistema. Reemplaza el
modelo anterior, que era una aplicación de sorteo con auto-registro público.

---

## 1. Qué estaba mal

El sistema anterior no era una versión incompleta de este: era otro sistema.

| Defecto | Evidencia |
|---|---|
| El rol `registrador` no existía | `auth.controller.js` solo ramifica en `admin` y `usuario` |
| `personas` era la tabla de autenticación | tenía `password_hash`; no había tabla `usuarios` |
| El alta era pública, sin token | `auth.routes.js`: `router.post('/register', register)` fuera del middleware |
| No se guardaba quién registraba a quién | no existía la columna `creado_por` |
| El historial era una ventana de 3 | `DELETE ... WHERE id NOT IN (... LIMIT 3)` |
| El historial se escribía fuera de transacción | cuatro `pool.query` sueltos en `editarPersona` |
| El cliente anulaba el bloqueo optimista | `ConflictResolver.kt` reescribía la versión y reintentaba: Last-Write-Wins ciego |
| El `DELETE` no validaba versión | `eliminarPersona` actualizaba sin comparar `version` |
| Las altas duplicadas se perdían | `SyncWorker.kt:158` marcaba y borraba de la outbox; el dato nunca salía del teléfono |
| Fuga de datos en `/sync` | sin filtro por rol: cualquier sesión bajaba toda la población |
| El cursor de sync podía saltear filas | `updated_at > since` con `now()` fijado al inicio de la transacción |

El bloqueo optimista del servidor (`WHERE id = $1 AND version = $2` que responde
409) era correcto y se conserva. Todo lo demás se rehace.

---

## 2. Roles

| Rol | Origen de la cuenta | Credenciales | Conectividad |
|---|---|---|---|
| `admin` | script de arranque del backend | usuario + contraseña | siempre online |
| `registrador` | lo crea el admin | usuario + contraseña | **offline para personas**, online para el resto |
| `usuario` | es una persona del padrón | tipo + número de documento | **offline para entrar y leer lo suyo** |

El admin puede crear **tantos registradores como haga falta**: son filas de
`usuarios` con `rol = 'registrador'`, sin límite de cantidad.

### Frontera de conectividad

| Rol | Funciona sin conexión | Qué necesita almacenado en el dispositivo |
|---|---|---|
| `admin` | nada | nada: cliente online delgado |
| `registrador` | registrar, editar y eliminar personas | Room + outbox + `SyncWorker` completos |
| `usuario` | entrar y leer su propio registro | Room: **una sola fila**, la suya. Sin outbox |

Consecuencia de arquitectura: **la outbox y la resolución de conflictos son
exclusivas del registrador.** El rol usuario es solo lectura, así que no puede
generar conflictos jamás: su copia local es una caché de lectura que se refresca
cuando hay conexión y se lee tal cual cuando no la hay. El admin no guarda nada
localmente.

La outbox transporta exactamente tres operaciones: `CREATE`, `UPDATE` y `DELETE`
de personas. Nada más.

### Login sin conexión

| Rol | Entra sin conexión | Cómo se valida |
|---|---|---|
| `admin` | no | siempre contra el servidor |
| `registrador` | sí | `LocalPasswordVerifier` contra el verificador guardado |
| `usuario` | sí | comparación directa contra la fila en caché |

**Registrador.** La contraseña nunca se guarda en claro. En el login online
exitoso se deriva y almacena un verificador cifrado; sin conexión se recalcula
sobre lo tecleado y se compara. Esto ya existe y se conserva.

**Usuario.** Acá no hay nada que derivar. Su credencial es el par
`(tipo_documento, numero_documento)`, que es exactamente lo que ya está guardado
en la fila que tiene en caché. El login sin conexión es una comparación de dos
campos contra esa fila.

> Es una consecuencia directa de haber decidido que el documento sea la única
> credencial: como no hay secreto, tampoco hay nada que verificar
> criptográficamente. La misma decisión que debilita el acceso es la que hace
> trivial el login sin conexión. Queda anotado como lo que es: un intercambio
> aceptado a conciencia, no un descuido.

### Restricción operativa: el primer login siempre es online

Ningún rol puede iniciar sesión **por primera vez** sin conexión, y por motivos
distintos:

- **Registrador**: sin un login online previo no existe el verificador contra el
  cual comparar.
- **Usuario**: su registro tiene que descargarse alguna vez. Quien lo creó fue
  un registrador, en otro dispositivo.

**Procedimiento con el registrador:** el admin crea la cuenta y el registrador
inicia sesión **antes de salir a campo**. Con la aplicación recién instalada y
sin señal, no entra.

**Procedimiento con el usuario:** entra una vez con conexión y a partir de ahí
el dispositivo le sirve sin señal, hasta que cierre sesión.

### Cierre de sesión y limpieza del dispositivo

Al cerrar sesión hay que **borrar la base local**. Si no, los datos del usuario
anterior quedan visibles para el siguiente que entre en ese mismo dispositivo, y
son datos de salud.

Con una excepción que es obligatoria: **no se puede cerrar sesión con la outbox
pendiente.** Borrar ahí destruiría trabajo de campo que nunca llegó al servidor.
La aplicación debe bloquear el cierre de sesión hasta que la cola se vacíe, y
decir cuántas operaciones faltan.

### Permisos sobre personas

- `registrador`: crea personas. Edita y elimina **solo las que él registró**
  (`personas.creado_por` igual al id del token).
- `admin`: ve todo, edita todo, elimina todo, resuelve la cola de conflictos.
- `usuario`: **solo lectura** de su propio registro.

---

## 3. Esquema

```sql
-- ---------------------------------------------------------------------------
-- usuarios: solo admin y registradores. Las personas del padrón NO están acá.
-- ---------------------------------------------------------------------------
CREATE TABLE usuarios (
    id            uuid PRIMARY KEY,
    usuario       varchar(60)  NOT NULL,
    password_hash varchar(255) NOT NULL,
    rol           varchar(20)  NOT NULL CHECK (rol IN ('admin', 'registrador')),
    activo        boolean      NOT NULL DEFAULT true,
    creado_por    uuid         REFERENCES usuarios(id),
    creado_en     timestamptz  NOT NULL DEFAULT now()
);

-- Sin distinguir mayúsculas: "Ana" y "ana" no pueden coexistir.
CREATE UNIQUE INDEX idx_usuarios_usuario ON usuarios (lower(usuario));

-- ---------------------------------------------------------------------------
-- personas: el padrón. Sin credenciales: ese era el defecto de raíz anterior.
-- ---------------------------------------------------------------------------
CREATE SEQUENCE personas_cambio_seq;

CREATE TABLE personas (
    id               uuid PRIMARY KEY,
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

    creado_por       uuid        NOT NULL REFERENCES usuarios(id),

    version          integer     NOT NULL DEFAULT 1,
    cambio_seq       bigint      NOT NULL DEFAULT nextval('personas_cambio_seq'),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz
);

-- El índice va sobre el PAR, no sobre el número solo: una CC y una TI pueden
-- compartir número y son personas distintas. El índice anterior rechazaba
-- altas legítimas. Es parcial: dar de baja libera el documento.
CREATE UNIQUE INDEX idx_personas_documento_activo
    ON personas (tipo_documento, numero_documento)
    WHERE deleted_at IS NULL;

-- La sincronización pagina por esta columna, no por updated_at.
CREATE UNIQUE INDEX idx_personas_cambio_seq ON personas (cambio_seq);

-- El registrador solo descarga lo suyo.
CREATE INDEX idx_personas_creado_por ON personas (creado_por);

-- El cursor se mantiene por trigger, no a mano en cada UPDATE. Es un
-- invariante, no una decisión por consulta: un UPDATE que olvide avanzarlo
-- deja ese cambio invisible para todos los dispositivos, y falla en silencio.
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

-- `version` a propósito NO se toca en el trigger: es lógica de negocio del
-- bloqueo optimista y la controla el controlador. El cursor es mecánico.

-- ---------------------------------------------------------------------------
-- historial_cambios: append-only, UNA FILA POR CAMPO.
--
-- La granularidad por campo no es un detalle: es lo que hace que el merge
-- automático y la fusión manual del admin sean la misma consulta.
--
-- Nunca se borra nada. El DELETE...LIMIT 3 del sistema anterior convertía la
-- auditoría en una ventana rodante de tres filas.
-- ---------------------------------------------------------------------------
CREATE TABLE historial_cambios (
    id             bigserial   PRIMARY KEY,
    persona_id     uuid        NOT NULL REFERENCES personas(id),
    version        integer     NOT NULL,   -- versión RESULTANTE del cambio
    operacion      varchar(10) NOT NULL
                   CHECK (operacion IN ('CREATE','UPDATE','DELETE')),
    campo          varchar(40),            -- NULL en CREATE y DELETE
    valor_anterior text,
    valor_nuevo    text,
    realizado_por  uuid        NOT NULL REFERENCES usuarios(id),
    realizado_en   timestamptz NOT NULL DEFAULT now(),

    -- De qué conflicto salió este cambio, si salió de uno. Sin esto la
    -- auditoría dice "el admin cambió el teléfono" pero no por qué, y
    -- reconstruirlo obligaría a correlacionar por hora.
    --
    -- Obliga a crear `conflictos` ANTES que esta tabla.
    conflicto_id   uuid        REFERENCES conflictos(id)
);

CREATE INDEX idx_historial_persona ON historial_cambios (persona_id, version);
CREATE INDEX idx_historial_merge   ON historial_cambios (persona_id, campo, version);

-- ---------------------------------------------------------------------------
-- conflictos: acá se salvan los datos que el sistema anterior tiraba.
-- ---------------------------------------------------------------------------
CREATE TABLE conflictos (
    id                   uuid        PRIMARY KEY,
    tipo                 varchar(24) NOT NULL
                         CHECK (tipo IN ('ALTA_DUPLICADA','EDICION_CONCURRENTE')),
    persona_existente_id uuid        NOT NULL REFERENCES personas(id),
    persona_id_cliente   uuid,       -- el UUID que generó el teléfono perdedor
    datos_enviados       jsonb       NOT NULL,
    version_base         integer,    -- solo en EDICION_CONCURRENTE
    enviado_por          uuid        NOT NULL REFERENCES usuarios(id),
    enviado_en           timestamptz NOT NULL DEFAULT now(),
    estado               varchar(12) NOT NULL DEFAULT 'PENDIENTE'
                         CHECK (estado IN ('PENDIENTE','RESUELTO','DESCARTADO')),
    resuelto_por         uuid        REFERENCES usuarios(id),
    resuelto_en          timestamptz,
    nota_resolucion      text
);

CREATE INDEX idx_conflictos_pendientes
    ON conflictos (enviado_en) WHERE estado = 'PENDIENTE';
```

---

## 4. Endpoints

### Autenticación

| Método | Ruta | Quién | Cuerpo |
|---|---|---|---|
| POST | `/api/auth/login-operador` | público | `{ usuario, password }` devuelve admin o registrador |
| POST | `/api/auth/login-persona` | público | `{ tipo_documento, numero_documento }` devuelve usuario |

El token lleva `{ id, rol }` para operadores y `{ rol: 'usuario', personaId }`
para personas.

> **Nota de seguridad, registrada explícitamente.** `login-persona` no pide
> secreto alguno: quien conozca el tipo y número de documento accede al
> registro. Fue una decisión del lado del negocio, tomada con el riesgo
> advertido. Mitigación mínima recomendada: limitar la tasa de intentos por IP
> y dejar traza de cada acceso.

### Usuarios — solo admin, solo online

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/usuarios` | crear registrador |
| GET | `/api/usuarios` | listar |
| PATCH | `/api/usuarios/:id` | activar, desactivar, cambiar contraseña |

**Primer admin:** script idempotente `backend/scripts/crear-admin.js`, que lee
`ADMIN_USER` y `ADMIN_PASSWORD` del entorno e inserta la fila si no existe. El
admin deja de vivir suelto en el `.env` y pasa a ser una fila de `usuarios`.

### Personas

| Método | Ruta | Quién | Offline |
|---|---|---|---|
| POST | `/api/personas` | registrador, admin | **sí** |
| PUT | `/api/personas/:id` | registrador (solo suyas), admin | **sí** |
| DELETE | `/api/personas/:id` | registrador (solo suyas), admin | **sí** |
| GET | `/api/personas/sync?desde=<seq>` | registrador (solo suyas), admin (todas) | descarga |
| GET | `/api/personas` | admin | no |
| GET | `/api/personas/:id` | admin, usuario (solo la suya) | **sí, desde caché** |
| GET | `/api/personas/:id/historial` | admin | no |

El rol usuario consulta `GET /api/personas/:id` con su propio id, guarda la
respuesta en Room y la lee desde ahí mientras no haya conexión. Refresca cuando
la recupera. No escribe nunca, así que no necesita outbox ni versión base.

El alta **deja de ser pública**. Todo el router exige token.

### Conflictos — solo admin, solo online

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/conflictos` | cola de pendientes, con la persona con la que chocó |
| GET | `/api/conflictos/mios` | **operador**: qué pasó con lo que él mandó |
| POST | `/api/conflictos/:id/fusionar` | `{ campos: { ... } }` elección campo por campo |
| POST | `/api/conflictos/:id/crear-como-nueva` | `{ tipo_documento, numero_documento }` corregidos |
| POST | `/api/conflictos/:id/descartar` | `{ nota }` |

---

## 5. Protocolo de sincronización

### Cursor

La descarga pagina por `cambio_seq`, no por `updated_at`:

```sql
SELECT ... FROM personas
WHERE cambio_seq > $1
  AND creado_por = $2      -- el filtro por rol cierra la fuga de datos
ORDER BY cambio_seq
LIMIT 500;
```

`now()` se fija al inicio de la transacción, así que con `updated_at` una
transacción que confirma tarde queda por debajo de un cursor ya avanzado y ese
cambio no se descarga nunca. La secuencia reduce el problema y además da un
orden total estable para paginar.

> **Riesgo residual:** una transacción puede tomar un valor de secuencia y
> confirmar después de que otra con valor mayor ya fue leída. Con transacciones
> de una sola sentencia la ventana es de milisegundos. Mitigación: una
> reconciliación completa (`desde=0`) al abrir sesión.

### Payload de edición: solo los campos tocados

```json
{
  "version_base": 3,
  "cambios": { "telefono": "3001234567" }
}
```

Mandar solo lo modificado es lo que habilita el merge. El sistema anterior
mandaba el registro entero, y entonces toda edición concurrente era un choque.

### Algoritmo de edición en el servidor

Todo dentro de **una** transacción:

```
BEGIN
  SELECT * FROM personas WHERE id = $1 FOR UPDATE

  si persona.version = version_base:
      aplicar directamente

  si no:
      campos_intermedios = SELECT DISTINCT campo FROM historial_cambios
                           WHERE persona_id = $1 AND version > version_base

      si campos_intermedios no interseca con las claves de cambios:
          -> MERGE. Nadie tocó estos campos. Se aplica.

      si no:
          -> CHOQUE REAL. INSERT INTO conflictos (EDICION_CONCURRENTE)
          -> COMMIT y responder 409 con conflicto_id

  UPDATE personas SET <campos>, version = version + 1
  -- cambio_seq y updated_at los pone el trigger. No hay que acordarse.
  INSERT INTO historial_cambios   -- una fila por campo cambiado
COMMIT
```

El `FOR UPDATE` y la transacción única son obligatorios: en el sistema anterior
el historial se escribía en consultas sueltas y dos ediciones concurrentes lo
dejaban mintiendo.

### Algoritmo de alta

```
BEGIN
  INSERT INTO personas (...) ON CONFLICT (id) DO NOTHING

  si insertó:
      INSERT INTO historial_cambios (operacion = 'CREATE')
      COMMIT -> 201

  si el id ya existía:
      COMMIT -> 200      -- reintento idempotente de la outbox

  si chocó el documento (23505):
      INSERT INTO conflictos (ALTA_DUPLICADA, datos_enviados = payload completo)
      COMMIT -> 409 con conflicto_id
```

**La línea clave:** el registro perdedor se persiste **en el servidor** antes de
responder. Recién entonces el teléfono puede sacarlo de la cola. En el sistema
anterior se marcaba en el celular y se borraba de la outbox: si ese teléfono se
rompía, el trabajo de campo desaparecía sin dejar rastro.

### Resolución del alta duplicada

El admin ve las dos versiones enfrentadas y elige:

- **Es la misma persona.** Fusiona campo por campo. Si el segundo registrador
  anotó un teléfono que el primero dejó vacío, ese dato se aprovecha en vez de
  tirarse. Queda en el historial que ambos participaron del registro.
- **Son dos personas distintas.** Alguien tipeó mal un dígito. El admin corrige
  el documento y el registro entra como persona nueva.

La fusión campo por campo del admin es **la misma maquinaria** que el merge
automático de ediciones. Se escribe una vez y sirve para los dos casos.

---

## 6. Aplicación Android

### Se conserva

El motor offline es lo más difícil de este proyecto y ya está bien resuelto.

- Room, DAOs y **patrón outbox**
- `SyncWorker` con sus fases PULL/PUSH, manejo de 401, reintentos y aislamiento
  por operación
- `WorkManagerSyncScheduler`
- `SessionManager` sobre `EncryptedSharedPreferences`
- `AuthInterceptor`, módulos de Hilt, separación domain/data/presentation
- Tema y componentes

### Se borra

- **`ConflictResolver.kt`**, completo. Implementaba Last-Write-Wins ciego: ante
  un 409 pedía la versión del servidor, reescribía el payload y reintentaba,
  pisando en silencio el cambio ajeno. No se corrige, se elimina. La resolución
  pasa a ser responsabilidad del servidor y del admin.
- `LocalPasswordVerifier` **aplicado a personas** y las contraseñas pendientes de
  `SessionManager`. Eran para el auto-registro offline, que ya no existe. El
  verificador sobrevive solo para el login offline de registradores.
- Todo lo relacionado con `ganador-semana`.

### Se reescribe

- Modelo `Persona` y `PersonaEntity`: campos nuevos, `creadoPor`
- `HistorialEntity`: pasa a granularidad por campo
- `PersonaRepositoryImpl.editarPersona`: manda solo los campos tocados y `version_base`
- Login: dos pantallas distintas, operador y persona
- Listado: vista de registrador (solo lo suyo) y vista de admin
- Estado local nuevo: `EN_REVISION`, para lo que quedó en la cola de conflictos
- Caché de lectura del rol usuario: guarda su propia fila y la muestra sin
  conexión. Es la ruta más simple de las tres, no toca la outbox
- Borrado de la base local al cerrar sesión, con bloqueo si la outbox no está
  vacía

---

## 7. Plan de tareas

Ordenado por dependencia. Cada bloque se puede verificar antes de seguir.

**Bloque 1 — Base de datos** — HECHO
1. ~~Escribir `backend/db/init/01_esquema.sql` con el esquema completo~~
2. Recrear el volumen de Postgres (destructivo, lo ejecuta el usuario) — pendiente
3. ~~Escribir `backend/scripts/crear-admin.js` idempotente~~

**Bloque 2 — Backend: autenticación y usuarios** — HECHO
4. ~~`auth.controller`: `login-operador` y `login-persona`~~
5. ~~`auth.middleware`: `verificarToken`, `requerirRol`, `requerirAdmin`,
   `requerirOperador`, `requerirPropiedadSobrePersona`~~
6. ~~`usuarios.controller` y sus rutas (alta de registradores)~~
6b. ~~`limitarIntentos.middleware`: límite de intentos por IP. No estaba en el
    plan; se agregó porque `login-persona` no pide contraseña y sin esto un
    barrido de números de documento es cuestión de minutos~~

**Bloque 3 — Backend: personas e historial** — HECHO
7. ~~`personas.controller`: alta transaccional con detección de duplicado~~
8. ~~Edición transaccional con merge por campo~~ (lógica pura en
   `src/domain/mergePersonas.js`, 19 tests unitarios)
9. ~~Baja con validación de versión~~
10. ~~`/sync` paginado por `cambio_seq` y filtrado por rol~~
11. ~~Historial por persona~~

**Bloque 4 — Backend: conflictos** — HECHO
12. ~~`conflictos.controller`: listar, fusionar, crear-como-nueva, descartar~~
12b. ~~`GET /mios`: el registrador consulta qué pasó con lo que mandó. Sin esto
     el estado `EN_REVISION` del teléfono no se resolvería nunca~~

> **El backend está terminado.** 154 verificaciones en verde: 38 unitarias y
> 116 de integración contra un Postgres real. Se corren con `npm run test:todo`;
> las instrucciones del Postgres de prueba están en la cabecera de cada arnés
> de `backend/test/`.

**Bloque 5 — Android: datos** — HECHO
13. ~~Entidades, DTOs y mappers nuevos; migración de Room~~
14. ~~`PersonaRepositoryImpl` con payload de cambios parciales~~ (+ coalescencia
    de la cola: dos ediciones de la misma persona se unifican en una)
15. ~~Borrar `ConflictResolver`; adaptar `SyncWorker` al nuevo 409~~ (+ fase 3:
    consultar qué resolvió el admin sobre lo que quedó en revisión)

**Bloque 6 — Android: interfaz** — HECHO
16. ~~Login de operador y login de persona, ambos con su ruta sin conexión~~
17. ~~Formulario con los campos nuevos~~
18. ~~Listado del registrador, con estado `EN_REVISION`~~
19. ~~Pantalla del rol usuario: su propio registro, leído de la caché local~~
20. ~~Vistas de admin: todas las personas, quién registró cada una~~
21. ~~Panel de conflictos del admin, con comparación campo por campo~~
22. ~~Cierre de sesión con borrado local y bloqueo por outbox pendiente~~

> `./gradlew assembleDebug` termina en BUILD SUCCESSFUL. Compilar no es
> funcionar: la verificación en un dispositivo es el Bloque 7.

**Bloque 6b — Identidad visual** — PENDIENTE
La paleta actual (`WinPlayPink`, `WinPlayPurple`, rosa neón sobre negro) viene
de la aplicación de sorteos y no se cambió en este trabajo. Para un padrón de
salud comunica lo contrario de lo que el sistema es: hay que reemplazarla por
una identidad sobria, con contraste suficiente para leerse al sol y una escala
de estados donde `EN_REVISION` no se confunda con un error.

**Bloque 7 — Verificación**

Backend, contra un Postgres real (`npm run test:todo`):
23. ~~Dos ediciones simultáneas sobre campos distintos: hay merge~~
24. ~~Dos ediciones simultáneas sobre el mismo campo: queda conflicto registrado~~
25. ~~Dos altas del mismo documento sin conexión: ninguna se pierde~~
26. ~~El registrador no puede editar ni leer lo que no registró~~

Android, en el emulador (`./gradlew testDebugUnitTest connectedDebugAndroidTest`):
27. ~~La cola no encola dos ediciones sueltas de la misma persona: las unifica~~
28. ~~La versión local no se incrementa: viaja la del servidor~~
29. ~~La edición manda solo los campos que cambiaron~~
30. ~~Dar de baja un alta sin enviar no le avisa al servidor~~
31. ~~No se puede borrar la base local con la cola llena~~
32. ~~La aplicación arranca, crea la base y muestra el login sin caerse~~

Pendiente de comprobar a mano en el dispositivo:
33. El usuario entra y ve su registro en modo avión, tras un login online previo
34. El registrador entra en modo avión, tras un login online previo
35. Al cerrar sesión no queda rastro del usuario anterior en el dispositivo

> Recuento: 154 verificaciones de backend, 22 unitarias de Android y 13
> instrumentadas sobre Room en un dispositivo. Total 189.
