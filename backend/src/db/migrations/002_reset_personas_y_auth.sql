-- Migración 002: reset de participantes y preparación de autenticación.
--
-- Elimina los registros de prueba existentes y agrega el esquema necesario
-- para autenticación real con contraseña.
--
-- ATENCIÓN: esta migración borra TODAS las filas de personas y de
-- historial_cambios. Verificar que exista un respaldo antes de ejecutarla.

BEGIN;

-- 1. Limpieza de los datos de prueba.
-- historial_cambios se borraría en cascada por la clave foránea, pero se
-- hace explícito para dejar la intención por escrito.
DELETE FROM historial_cambios;
DELETE FROM personas;

-- 2. Contraseña del participante.
-- Se puede declarar NOT NULL sin valor por defecto porque la tabla quedó
-- vacía en el paso anterior. El largo cubre bcrypt (60) y argon2 (~95).
ALTER TABLE personas
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255) NOT NULL;

-- 3. Un documento no puede participar dos veces.
-- El índice es parcial: si un registro se da de baja (deleted_at), ese
-- documento vuelve a quedar disponible para un registro nuevo.
CREATE UNIQUE INDEX IF NOT EXISTS idx_personas_documento_activo
    ON personas (documento)
    WHERE deleted_at IS NULL;

-- 4. Índice para el endpoint de sincronización incremental (/api/personas/sync),
-- que filtra por updated_at en cada llamada de cada dispositivo.
CREATE INDEX IF NOT EXISTS idx_personas_updated_at
    ON personas (updated_at);

COMMIT;
