/**
 * Deja la base de pruebas vacía y con un admin recién creado.
 *
 * Los tres arneses de integración crean los mismos registradores y esperan
 * empezar de cero, así que hay que reiniciar entre uno y otro.
 *
 * Se niega a correr si la base no parece de pruebas. No es paranoia: un
 * TRUNCATE apuntado sin querer a la base de producción no tiene vuelta atrás,
 * y estas variables se pasan a mano.
 */

const bcrypt = require('bcryptjs');
const { randomUUID } = require('crypto');

const NOMBRES_PERMITIDOS = ['padron', 'padron_test', 'test'];

const nombreBase = process.env.DB_NAME || '';
if (!NOMBRES_PERMITIDOS.includes(nombreBase)) {
    console.error(
        `Este script solo corre contra una base de pruebas.\n` +
            `DB_NAME es "${nombreBase}" y se esperaba una de: ${NOMBRES_PERMITIDOS.join(', ')}.`
    );
    process.exit(1);
}

const pool = require('../src/db/pool');

const USUARIO_ADMIN = process.env.ADMIN_USER || 'admin';
const PASSWORD_ADMIN = process.env.ADMIN_PASSWORD || 'claveDeIntegracion123';

async function main() {
    await pool.query('TRUNCATE conflictos, historial_cambios, personas, usuarios CASCADE');

    await pool.query(
        `INSERT INTO usuarios (id, usuario, password_hash, rol)
         VALUES ($1, $2, $3, 'admin')`,
        [randomUUID(), USUARIO_ADMIN, await bcrypt.hash(PASSWORD_ADMIN, 12)]
    );

    console.log(`base "${nombreBase}" reiniciada, admin "${USUARIO_ADMIN}" listo`);
}

main()
    .catch((error) => {
        console.error(error);
        process.exit(1);
    })
    .finally(() => pool.end());
