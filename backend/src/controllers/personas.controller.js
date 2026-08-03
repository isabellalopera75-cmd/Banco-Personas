const pool = require('../db/pool');

// GET /api/personas — trae todos los registros activos
const obtenerPersonas = async (req, res) => {
    try {
        const resultado = await pool.query(
            'SELECT * FROM personas WHERE deleted_at IS NULL ORDER BY nombre'
        );
        res.json(resultado.rows);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: 'Error al obtener personas' });
    }
};

// POST /api/personas — crea un registro nuevo
const crearPersona = async (req, res) => {
    const { id, nombre, documento, telefono } = req.body;
    try {
        const resultado = await pool.query(
            `INSERT INTO personas (id, nombre, documento, telefono, version)
             VALUES ($1, $2, $3, $4, 1)
             ON CONFLICT (id) DO NOTHING
             RETURNING *`,
            [id, nombre, documento, telefono]
        );
        res.status(201).json(resultado.rows[0]);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: 'Error al crear persona' });
    }
};

// PUT /api/personas/:id — edita un registro, valida versión
const editarPersona = async (req, res) => {
    const { id } = req.params;
    const { nombre, documento, telefono, version } = req.body;
    try {
        const resultado = await pool.query(
            `UPDATE personas
             SET nombre = $1, documento = $2, telefono = $3,
                 version = version + 1, updated_at = now()
             WHERE id = $4 AND version = $5
             RETURNING *`,
            [nombre, documento, telefono, id, version]
        );
        if (resultado.rows.length === 0) {
            // No actualizó ninguna fila: la versión no coincide = conflicto
            return res.status(409).json({ error: 'Conflicto de versión' });
        }
        res.json(resultado.rows[0]);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: 'Error al editar persona' });
    }
};

// DELETE /api/personas/:id — soft delete
const eliminarPersona = async (req, res) => {
    const { id } = req.params;
    try {
        await pool.query(
            'UPDATE personas SET deleted_at = now() WHERE id = $1',
            [id]
        );
        res.status(204).send();
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: 'Error al eliminar persona' });
    }
};

// GET /api/personas/sync?since=... — trae cambios desde una fecha
const obtenerCambios = async (req, res) => {
    const { since } = req.query;
    try {
        const resultado = await pool.query(
            'SELECT * FROM personas WHERE updated_at > $1',
            [since || '1970-01-01']
        );
        res.json(resultado.rows);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: 'Error al obtener cambios' });
    }
};

module.exports = {
    obtenerPersonas,
    crearPersona,
    editarPersona,
    eliminarPersona,
    obtenerCambios
};