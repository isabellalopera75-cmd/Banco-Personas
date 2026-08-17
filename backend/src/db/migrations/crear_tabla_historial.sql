CREATE TABLE IF NOT EXISTS historial_cambios (
    id UUID PRIMARY KEY,
    persona_id UUID REFERENCES personas(id) ON DELETE CASCADE,
    nombre_anterior VARCHAR(150),
    documento_anterior VARCHAR(20),
    telefono_anterior VARCHAR(20),
    creado_en TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_historial_persona_id ON historial_cambios(persona_id);
