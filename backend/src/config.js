// Configuración validada al arranque.
//
// Si falta una variable obligatoria el proceso NO arranca. Es preferible un
// fallo ruidoso e inmediato al inicio que un 500 silencioso en cada request:
// un servidor que responde /health pero no puede consultar la base de datos
// es más difícil de diagnosticar que uno que directamente no levanta.

require('dotenv').config();

const VARIABLES_REQUERIDAS = [
    'DB_USER',
    'DB_PASSWORD',
    'DB_HOST',
    'DB_PORT',
    'DB_NAME',
    'JWT_SECRET',
    'ADMIN_USER',
    'ADMIN_PASSWORD_HASH',
];

const faltantes = VARIABLES_REQUERIDAS.filter((clave) => !process.env[clave]);

if (faltantes.length > 0) {
    throw new Error(
        `Faltan variables de entorno obligatorias: ${faltantes.join(', ')}. ` +
            'Revisar el archivo .env o la configuración del contenedor.'
    );
}

module.exports = {
    port: Number(process.env.PORT) || 3000,

    db: {
        user: process.env.DB_USER,
        password: process.env.DB_PASSWORD,
        host: process.env.DB_HOST,
        port: Number(process.env.DB_PORT),
        database: process.env.DB_NAME,
    },

    jwt: {
        secret: process.env.JWT_SECRET,
        expiresIn: process.env.JWT_EXPIRES_IN || '7d',
    },

    admin: {
        usuario: process.env.ADMIN_USER,
        passwordHash: process.env.ADMIN_PASSWORD_HASH,
    },
};
