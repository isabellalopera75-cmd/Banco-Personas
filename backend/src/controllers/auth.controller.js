const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../db/pool');
const config = require('../config');

const firmarToken = (payload) =>
    jwt.sign(payload, config.jwt.secret, { expiresIn: config.jwt.expiresIn });

/**
 * Hash señuelo, calculado una vez al arrancar.
 *
 * Si ante un usuario inexistente respondiéramos sin llamar a bcrypt, esa
 * respuesta llegaría en microsegundos y la de una contraseña incorrecta en
 * cientos de milisegundos. Esa diferencia es medible desde afuera y permite
 * averiguar qué usuarios existen sin acertar una sola contraseña.
 *
 * Comparando siempre contra algo, las dos respuestas tardan lo mismo.
 */
const HASH_SENUELO = bcrypt.hashSync('ninguna cuenta usa esta contraseña', 12);

/**
 * POST /api/auth/login-operador
 *
 * Entrada de admin y registrador: los que tienen cuenta en `usuarios`.
 */
const loginOperador = async (req, res) => {
    const { usuario, password } = req.body || {};

    if (!usuario || !password) {
        return res
            .status(400)
            .json({ error: 'El usuario y la contraseña son obligatorios' });
    }

    try {
        // lower() para coincidir con el índice único de la tabla: si ahí
        // "Ana" y "ana" son la misma cuenta, acá también tienen que serlo.
        const resultado = await pool.query(
            `SELECT id, usuario, password_hash, rol, activo
               FROM usuarios
              WHERE lower(usuario) = lower($1)`,
            [String(usuario).trim()]
        );

        const fila = resultado.rows[0];

        const passwordCoincide = await bcrypt.compare(
            String(password),
            fila ? fila.password_hash : HASH_SENUELO
        );

        // Mismo mensaje para usuario inexistente y contraseña incorrecta: si
        // fueran distintos, la respuesta permitiría enumerar cuentas.
        if (!fila || !passwordCoincide) {
            return res.status(401).json({ error: 'Usuario o contraseña incorrectos' });
        }

        // Se verifica DESPUÉS de la contraseña. Al revés, un 403 revelaría que
        // esa cuenta existe a quien no sabe la contraseña.
        if (!fila.activo) {
            return res.status(403).json({ error: 'La cuenta está desactivada' });
        }

        return res.json({
            rol: fila.rol,
            token: firmarToken({ sub: fila.id, rol: fila.rol }),
            usuario: { id: fila.id, usuario: fila.usuario, rol: fila.rol },
        });
    } catch (error) {
        console.error(error);
        return res.status(500).json({ error: 'Error en el servidor' });
    }
};

// Columnas de `personas` que pueden salir del servidor.
const CAMPOS_PERSONA = `id, tipo_documento, numero_documento,
                        primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,
                        fecha_nacimiento, sexo, telefono,
                        creado_por, version, cambio_seq, updated_at`;

/**
 * POST /api/auth/login-persona
 *
 * Entrada del rol usuario: una persona del padrón consultando sus datos.
 *
 * No pide contraseña. La credencial es el par tipo + número de documento, que
 * NO es un secreto: quien lo conozca entra. Fue una decisión de negocio tomada
 * con el riesgo advertido, y está documentada en docs/diseno-tres-roles.md.
 *
 * Por eso esta ruta va detrás del limitador de intentos: es la única barrera
 * que hay contra un barrido de números de documento.
 */
const loginPersona = async (req, res) => {
    const { tipo_documento, numero_documento } = req.body || {};

    if (!tipo_documento || !numero_documento) {
        return res
            .status(400)
            .json({ error: 'El tipo y el número de documento son obligatorios' });
    }

    try {
        const resultado = await pool.query(
            `SELECT ${CAMPOS_PERSONA}
               FROM personas
              WHERE tipo_documento = $1
                AND numero_documento = $2
                AND deleted_at IS NULL`,
            [String(tipo_documento).trim().toUpperCase(), String(numero_documento).trim()]
        );

        const persona = resultado.rows[0];

        if (!persona) {
            return res.status(401).json({ error: 'No hay un registro con ese documento' });
        }

        return res.json({
            rol: 'usuario',
            token: firmarToken({ sub: persona.id, rol: 'usuario' }),
            persona,
        });
    } catch (error) {
        // 22P02 / 23514: tipo_documento fuera del CHECK de la tabla. Es una
        // entrada inválida del cliente, no una falla del servidor.
        if (error.code === '22P02' || error.code === '23514') {
            return res.status(400).json({ error: 'Tipo de documento no válido' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error en el servidor' });
    }
};

module.exports = {
    loginOperador,
    loginPersona,
};
