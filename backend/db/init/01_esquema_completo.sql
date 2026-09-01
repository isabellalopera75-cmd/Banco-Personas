-- Esquema completo para una base de datos nueva.
--
-- Postgres ejecuta este archivo automáticamente la primera vez que se crea
-- el volumen de datos (docker-entrypoint-initdb.d). Sobre una base que ya
-- tiene datos no corre: para esos casos están las migraciones numeradas de
-- src/db/migrations.

CREATE TABLE IF NOT EXISTS personas (
    id            uuid PRIMARY KEY,
    nombre        varchar(150) NOT NULL,
    documento     varchar(20)  NOT NULL,
    telefono      varchar(20),
    password_hash varchar(255) NOT NULL,
    version       integer      NOT NULL DEFAULT 1,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted_at    timestamptz
);

-- Un documento no puede participar dos veces. Es parcial: si un registro se
-- da de baja, ese documento vuelve a quedar disponible.
CREATE UNIQUE INDEX IF NOT EXISTS idx_personas_documento_activo
    ON personas (documento)
    WHERE deleted_at IS NULL;

-- La sincronización incremental filtra por esta columna en cada llamada
-- de cada dispositivo.
CREATE INDEX IF NOT EXISTS idx_personas_updated_at
    ON personas (updated_at);

CREATE TABLE IF NOT EXISTS historial_cambios (
    id                 uuid PRIMARY KEY,
    persona_id         uuid REFERENCES personas(id) ON DELETE CASCADE,
    nombre_anterior    varchar(150),
    documento_anterior varchar(20),
    telefono_anterior  varchar(20),
    creado_en          timestamptz DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_historial_persona_id
    ON historial_cambios (persona_id);
