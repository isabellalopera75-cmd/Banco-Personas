-- Migración 003: alinea una base ya existente con el esquema que espera el código.
--
-- Se escribió para la base de producción (winplaydb), que nunca recibió la
-- migración 002 y quedó con el esquema anterior a la autenticación: sin
-- password_hash. Ese es el motivo por el que ningún registro se guardaba —
-- el INSERT de /api/auth/register nombra esa columna y Postgres lo rechazaba
-- con 42703, que el controlador traducía a un 500 genérico.
--
-- A diferencia de la 002, esta no da por sentado qué columnas existen: usa
-- IF NOT EXISTS en todo, así que es segura de correr sobre una base a medio
-- migrar y se puede repetir sin romper nada.
--
-- ATENCIÓN: borra TODAS las filas de personas y de historial_cambios.
-- Es inevitable, no una decisión de estilo: password_hash se declara NOT NULL
-- sin valor por defecto, y Postgres solo acepta eso sobre una tabla vacía.
-- Hacer un respaldo antes si los datos importan.

BEGIN;

-- 1. El historial puede no existir todavía en esta base. Se crea antes de
--    vaciarlo, porque el DELETE de abajo fallaría sobre una tabla ausente.
CREATE TABLE IF NOT EXISTS historial_cambios (
    id                 uuid PRIMARY KEY,
    persona_id         uuid REFERENCES personas(id) ON DELETE CASCADE,
    nombre_anterior    varchar(150),
    documento_anterior varchar(20),
    telefono_anterior  varchar(20),
    creado_en          timestamptz DEFAULT CURRENT_TIMESTAMP
);

-- 2. Limpieza. Primero el historial: tiene una clave foránea contra personas.
DELETE FROM historial_cambios;
DELETE FROM personas;

-- 3. Columnas que el código espera y esta base no tiene.
--    Se agrega password_hash como nullable y recién después se le exige
--    NOT NULL: sobre la tabla ya vacía la restricción entra sin conflicto.
ALTER TABLE personas ADD COLUMN IF NOT EXISTS password_hash varchar(255);
ALTER TABLE personas ALTER COLUMN password_hash SET NOT NULL;

ALTER TABLE personas ADD COLUMN IF NOT EXISTS version    integer     NOT NULL DEFAULT 1;
ALTER TABLE personas ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE personas ADD COLUMN IF NOT EXISTS deleted_at timestamptz;

-- 4. Un documento no puede participar dos veces. El índice es parcial: si un
--    registro se da de baja, ese documento vuelve a quedar disponible.
CREATE UNIQUE INDEX IF NOT EXISTS idx_personas_documento_activo
    ON personas (documento)
    WHERE deleted_at IS NULL;

-- 5. /api/personas/sync filtra por updated_at en cada llamada de cada
--    dispositivo, y el historial se consulta siempre por persona_id.
CREATE INDEX IF NOT EXISTS idx_personas_updated_at
    ON personas (updated_at);

CREATE INDEX IF NOT EXISTS idx_historial_persona_id
    ON historial_cambios (persona_id);

COMMIT;
