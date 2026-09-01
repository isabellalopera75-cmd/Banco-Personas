const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../db/pool');
const config = require('../config');

const firmarToken = (payload) =>
    jwt.sign(payload, config.jwt.secret, { expiresIn: config.jwt.expiresIn });

const login = async (req, res) => {
    const { rol, nombre, documento, password } = req.body;

    if (!password) {
        return res.status(400).json({ success: false, error: 'La contraseña es obligatoria' });
    }

    try {
        if (rol === 'admin') {
            const usuarioCoincide = nombre === config.admin.usuario;
            const passwordCoincide = await bcrypt.compare(password, config.admin.passwordHash);

            if (!usuarioCoincide || !passwordCoincide) {
                return res
                    .status(401)
                    .json({ success: false, error: 'Credenciales incorrectas' });
            }

            return res.json({
                success: true,
                rol: 'admin',
                token: firmarToken({ rol: 'admin' }),
            });
        }

        if (rol === 'usuario') {
            if (!documento) {
                return res
                    .status(400)
                    .json({ success: false, error: 'El documento es obligatorio' });
            }

            const resultado = await pool.query(
                'SELECT * FROM personas WHERE documento = $1 AND deleted_at IS NULL',
                [documento]
            );

            const persona = resultado.rows[0];
            const passwordCoincide = persona
                ? await bcrypt.compare(password, persona.password_hash)
                : false;

            if (!persona || !passwordCoincide) {
                // Mismo mensaje para documento inexistente y contraseña errónea:
                // así la respuesta no permite enumerar qué documentos existen.
                return res
                    .status(401)
                    .json({ success: false, error: 'Documento o contraseña incorrectos' });
            }

            // El hash nunca sale del servidor.
            const { password_hash, ...personaPublica } = persona;

            return res.json({
                success: true,
                rol: 'usuario',
                persona: personaPublica,
                token: firmarToken({ rol: 'usuario', personaId: persona.id }),
            });
        }

        return res.status(400).json({ success: false, error: 'Rol no válido' });
    } catch (error) {
        console.error(error);
        return res.status(500).json({ success: false, error: 'Error en el servidor' });
    }
};

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const COSTO_BCRYPT = 12;
const LARGO_MINIMO_PASSWORD = 8;

// Alta de participante. Es pública: se registra quien todavía no tiene cuenta.
//
// El id lo genera el dispositivo para que el alta funcione sin conexión, así
// que la operación tiene que ser idempotente: la outbox puede reintentar el
// mismo alta varias veces y todas tienen que devolver el mismo resultado.
const register = async (req, res) => {
    const { id, nombre, documento, telefono, password } = req.body;

    if (!UUID_V4.test(String(id || ''))) {
        return res
            .status(400)
            .json({ success: false, error: 'El id debe ser un UUID v4 válido' });
    }
    if (!nombre || !String(nombre).trim()) {
        return res.status(400).json({ success: false, error: 'El nombre es obligatorio' });
    }
    if (!documento || !String(documento).trim()) {
        return res.status(400).json({ success: false, error: 'El documento es obligatorio' });
    }
    if (!password || String(password).length < LARGO_MINIMO_PASSWORD) {
        return res.status(400).json({
            success: false,
            error: `La contraseña debe tener al menos ${LARGO_MINIMO_PASSWORD} caracteres`,
        });
    }

    try {
        const passwordHash = await bcrypt.hash(String(password), COSTO_BCRYPT);

        const insercion = await pool.query(
            `INSERT INTO personas (id, nombre, documento, telefono, password_hash, version)
             VALUES ($1, $2, $3, $4, $5, 1)
             ON CONFLICT (id) DO NOTHING
             RETURNING *`,
            [
                id,
                String(nombre).trim(),
                String(documento).trim(),
                telefono ? String(telefono).trim() : null,
                passwordHash,
            ]
        );

        let persona = insercion.rows[0];

        if (!persona) {
            // El id ya existía: es un reintento de la outbox. Se responde lo
            // mismo que la primera vez, pero solo si la contraseña coincide.
            const existente = await pool.query('SELECT * FROM personas WHERE id = $1', [id]);
            persona = existente.rows[0];

            const passwordCoincide = persona
                ? await bcrypt.compare(String(password), persona.password_hash)
                : false;

            if (!passwordCoincide) {
                return res
                    .status(409)
                    .json({ success: false, error: 'Ya existe un registro con ese id' });
            }
        }

        const { password_hash, ...personaPublica } = persona;

        return res.status(201).json({
            success: true,
            rol: 'usuario',
            persona: personaPublica,
            token: firmarToken({ rol: 'usuario', personaId: persona.id }),
        });
    } catch (error) {
        // 23505: violación del índice único de documento.
        if (error.code === '23505') {
            return res
                .status(409)
                .json({ success: false, error: 'Ese documento ya está registrado' });
        }
        console.error(error);
        return res.status(500).json({ success: false, error: 'Error en el servidor' });
    }
};

module.exports = {
    login,
    register,
};
