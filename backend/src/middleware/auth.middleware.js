const jwt = require('jsonwebtoken');
const config = require('../config');
const pool = require('../db/pool');

/**
 * Verifica el token del header Authorization y expone el payload en
 * `req.usuario`.
 *
 * El payload tiene siempre la misma forma para los tres roles:
 *   { sub: <id>, rol: 'admin' | 'registrador' | 'usuario' }
 *
 * Para admin y registrador, `sub` es un id de `usuarios`.
 * Para el rol usuario, `sub` es un id de `personas`: esa persona no tiene
 * cuenta, es su propio registro del padrón.
 */
const verificarToken = (req, res, next) => {
    const header = req.headers.authorization || '';
    const [esquema, token] = header.split(' ');

    if (esquema !== 'Bearer' || !token) {
        return res.status(401).json({ error: 'Token no provisto' });
    }

    try {
        req.usuario = jwt.verify(token, config.jwt.secret);
        return next();
    } catch (error) {
        return res.status(401).json({ error: 'Token inválido o expirado' });
    }
};

/**
 * Restringe una ruta a los roles indicados.
 *
 * Es una fábrica y no tres middlewares sueltos porque la lista de roles queda
 * escrita en la propia ruta. Leer `requerirRol('admin')` al lado del endpoint
 * dice más que un nombre que hay que ir a buscar a otro archivo.
 */
const requerirRol =
    (...rolesPermitidos) =>
    (req, res, next) => {
        if (req.usuario && rolesPermitidos.includes(req.usuario.rol)) {
            return next();
        }
        return res.status(403).json({ error: 'No tiene permisos para esta operación' });
    };

const requerirAdmin = requerirRol('admin');
const requerirOperador = requerirRol('admin', 'registrador');

/**
 * Autoriza a escribir sobre una persona.
 *
 * La regla: el admin puede sobre cualquiera; el registrador solo sobre las que
 * registró él; el rol usuario nunca, ni sobre su propio registro — es de solo
 * lectura por decisión de negocio.
 *
 * Recibe la consulta por parámetro en lugar de usar el pool directamente. Eso
 * permite probar la regla de autorización sin levantar una base de datos, que
 * es exactamente donde se cometen los errores de permisos que después nadie
 * detecta hasta que alguien edita lo que no debía.
 */
const crearRequerirPropiedadSobrePersona = (obtenerCreador) => async (req, res, next) => {
    const usuario = req.usuario;

    if (!usuario) {
        return res.status(403).json({ error: 'No tiene permisos para esta operación' });
    }

    // El admin no cuesta una consulta: puede sobre todo, no hace falta mirar
    // de quién es la persona.
    if (usuario.rol === 'admin') {
        return next();
    }

    if (usuario.rol !== 'registrador') {
        return res.status(403).json({ error: 'No tiene permisos para esta operación' });
    }

    try {
        const creador = await obtenerCreador(req.params.id);

        // Se distingue de un 403 a propósito. Responder 403 sobre algo que no
        // existe le confirma a quien prueba ids al azar que ese id sí existe y
        // pertenece a otro.
        if (!creador) {
            return res.status(404).json({ error: 'Persona no encontrada' });
        }

        if (creador !== usuario.sub) {
            return res
                .status(403)
                .json({ error: 'Solo puede operar sobre las personas que registró' });
        }

        return next();
    } catch (error) {
        // 22P02: el id de la ruta no es un uuid. Es una ruta inexistente,
        // no una falla del servidor.
        if (error.code === '22P02') {
            return res.status(404).json({ error: 'Persona no encontrada' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al verificar permisos' });
    }
};

/**
 * A propósito NO filtra por `deleted_at`.
 *
 * Ser el registrador de una persona no deja de ser cierto porque el registro
 * se haya dado de baja. Con el filtro puesto, reintentar una baja ya aplicada
 * — cosa que la outbox hace todo el tiempo — devolvía 404 desde el middleware,
 * sin llegar nunca al controlador que sabe responder "ya estaba eliminada".
 *
 * No abre nada: el controlador de edición sigue exigiendo `deleted_at IS NULL`
 * para poder modificar.
 */
const obtenerCreadorEnLaBase = async (personaId) => {
    const resultado = await pool.query('SELECT creado_por FROM personas WHERE id = $1', [
        personaId,
    ]);
    return resultado.rows[0] ? resultado.rows[0].creado_por : null;
};

const requerirPropiedadSobrePersona =
    crearRequerirPropiedadSobrePersona(obtenerCreadorEnLaBase);

module.exports = {
    verificarToken,
    requerirRol,
    requerirAdmin,
    requerirOperador,
    requerirPropiedadSobrePersona,
    // Se exporta la fábrica para los tests: permite inyectar la consulta.
    crearRequerirPropiedadSobrePersona,
};
