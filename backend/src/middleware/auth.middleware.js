const jwt = require('jsonwebtoken');
const config = require('../config');

// Verifica el token del header Authorization y expone el payload en req.usuario.
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

// Restringe la ruta al administrador.
const requerirAdmin = (req, res, next) => {
    if (req.usuario && req.usuario.rol === 'admin') {
        return next();
    }
    return res.status(403).json({ error: 'Requiere permisos de administrador' });
};

// Un participante solo puede operar sobre su propio registro.
// El administrador puede operar sobre cualquiera.
const requerirPropietarioOAdmin = (req, res, next) => {
    if (req.usuario && req.usuario.rol === 'admin') {
        return next();
    }
    if (req.usuario && req.usuario.personaId === req.params.id) {
        return next();
    }
    return res.status(403).json({ error: 'No puede operar sobre registros de otra persona' });
};

module.exports = {
    verificarToken,
    requerirAdmin,
    requerirPropietarioOAdmin,
};
