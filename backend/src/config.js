// Configuración validada al arranque.
//
// Si falta una variable obligatoria el proceso NO arranca. Es preferible un
// fallo ruidoso e inmediato al inicio que un 500 silencioso en cada request:
// un servidor que responde /health pero no puede consultar la base de datos
// es más difícil de diagnosticar que uno que directamente no levanta.

require('dotenv').config();

// ADMIN_USER y ADMIN_PASSWORD_HASH ya no figuran acá, y no es un olvido: el
// administrador dejó de vivir en el entorno y pasó a ser una fila de
// `usuarios`, igual que cualquier registrador. El servidor ya no necesita
// saber nada de él para arrancar.
//
// Quien sí las necesita es scripts/crear-admin.js, que las lee por su cuenta
// y usa ADMIN_PASSWORD en claro (hashea él mismo) en lugar de un hash ya hecho.
const VARIABLES_REQUERIDAS = [
    'DB_USER',
    'DB_PASSWORD',
    'DB_HOST',
    'DB_PORT',
    'DB_NAME',
    'JWT_SECRET',
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
};
