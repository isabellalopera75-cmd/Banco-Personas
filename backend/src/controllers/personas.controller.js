const pool = require('../db/pool');

// Columnas que pueden salir del servidor.
// Nunca incluir password_hash: un SELECT * lo filtraría a todos los clientes.
const CAMPOS_PUBLICOS = 'id, nombre, documento, telefono, version, updated_at, deleted_at';

// GET /api/personas — trae todos los registros activos
const obtenerPersonas = async (req, res) => {
    try {
        const resultado = await pool.query(
            `SELECT ${CAMPOS_PUBLICOS} FROM personas WHERE deleted_at IS NULL ORDER BY nombre`
        );
        res.json(resultado.rows);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: 'Error al obtener personas' });
    }
};

// PUT /api/personas/:id — edita un registro, valida versión
const editarPersona = async (req, res) => {
    const { id } = req.params;
    const { nombre, documento, telefono, version } = req.body;
    try {
        // Obtener datos antiguos para el historial
        const personaAnterior = await pool.query(
            `SELECT ${CAMPOS_PUBLICOS} FROM personas WHERE id = $1`,
            [id]
        );
        if (personaAnterior.rows.length === 0) return res.status(404).json({ error: 'No encontrada' });

        const oldData = personaAnterior.rows[0];

        const resultado = await pool.query(
            `UPDATE personas
             SET nombre = $1, documento = $2, telefono = $3,
                 version = version + 1, updated_at = now()
             WHERE id = $4 AND version = $5
             RETURNING ${CAMPOS_PUBLICOS}`,
            [nombre, documento, telefono, id, version]
        );
        if (resultado.rows.length === 0) {
            // No actualizó ninguna fila: la versión no coincide = conflicto.
            // El código lo distingue del otro 409 de este endpoint: este se
            // resuelve solo reintentando con la versión del servidor, el del
            // documento repetido no se resuelve nunca por más que se insista.
            return res
                .status(409)
                .json({ error: 'Conflicto de versión', codigo: 'CONFLICTO_VERSION' });
        }

        // Si los datos realmente cambiaron, guardar en historial
        if (oldData.nombre !== nombre || oldData.documento !== documento || oldData.telefono !== telefono) {
            const { randomUUID } = require('crypto');
            await pool.query(
                `INSERT INTO historial_cambios (id, persona_id, nombre_anterior, documento_anterior, telefono_anterior)
                 VALUES ($1, $2, $3, $4, $5)`,
                [randomUUID(), id, oldData.nombre, oldData.documento, oldData.telefono]
            );

            // Mantener solo los últimos 3 cambios
            await pool.query(
                `DELETE FROM historial_cambios
                 WHERE persona_id = $1 AND id NOT IN (
                     SELECT id FROM historial_cambios
                     WHERE persona_id = $1
                     ORDER BY creado_en DESC
                     LIMIT 3
                 )`,
                [id]
            );
        }

        res.json(resultado.rows[0]);
    } catch (error) {
        // 23505: violación del índice único de documento.
        //
        // El alta ya contemplaba este caso; la edición no, y salía como 500.
        // Para el cliente un 500 es una falla transitoria: reintentaba cuatro
        // veces contra un error que nunca iba a resolverse y terminaba
        // descartando el cambio en silencio.
        if (error.code === '23505') {
            return res.status(409).json({
                error: 'Ese documento ya pertenece a otro participante',
                codigo: 'DOCUMENTO_DUPLICADO',
            });
        }
        console.error(error);
        res.status(500).json({ error: 'Error al editar persona' });
    }
};

// GET /api/personas/:id/historial — obtiene el historial de una persona
const obtenerHistorial = async (req, res) => {
    const { id } = req.params;
    try {
        const resultado = await pool.query(
            'SELECT * FROM historial_cambios WHERE persona_id = $1 ORDER BY creado_en DESC LIMIT 3',
            [id]
        );
        res.json(resultado.rows);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: 'Error al obtener historial' });
    }
};

// DELETE /api/personas/:id — soft delete
const eliminarPersona = async (req, res) => {
    const { id } = req.params;
    try {
        // updated_at también se toca: /api/personas/sync filtra por esa columna,
        // así que sin esto la baja nunca llegaría a los dispositivos y el
        // registro quedaría vivo para siempre en cada celular.
        await pool.query(
            'UPDATE personas SET deleted_at = now(), updated_at = now() WHERE id = $1',
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
            `SELECT ${CAMPOS_PUBLICOS} FROM personas WHERE updated_at > $1`,
            [since || '1970-01-01']
        );
        res.json(resultado.rows);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: 'Error al obtener cambios' });
    }
};

// GET /api/personas/ganador-semana
const obtenerGanadorSemana = async (req, res) => {
    try {
        const resultado = await pool.query(
            `SELECT ${CAMPOS_PUBLICOS} FROM personas WHERE deleted_at IS NULL ORDER BY id`
        );

        const personas = resultado.rows;
        if (personas.length === 0) {
            return res.status(404).json({ error: 'No hay participantes' });
        }

        // Obtener año y semana actual
        const now = new Date();
        const start = new Date(now.getFullYear(), 0, 1);
        const days = Math.floor((now - start) / (24 * 60 * 60 * 1000));
        const weekNumber = Math.ceil(days / 7);
        const year = now.getFullYear();

        // Generar un índice pseudo-aleatorio predecible basado en semana y año
        let seed = (year * 100) + weekNumber;
        seed = (seed * 9301 + 49297) % 233280;
        const random = seed / 233280;

        const winnerIndex = Math.floor(random * personas.length);
        const winner = personas[winnerIndex];

        res.json(winner);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: 'Error al obtener ganador' });
    }
};

module.exports = {
    obtenerPersonas,
    editarPersona,
    eliminarPersona,
    obtenerCambios,
    obtenerHistorial,
    obtenerGanadorSemana
};
