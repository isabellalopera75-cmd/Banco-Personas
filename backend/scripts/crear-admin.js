#!/usr/bin/env node
/**
 * Crea el primer administrador del sistema.
 *
 * El admin dejó de vivir suelto en el .env como usuario y hash: ahora es una
 * fila de `usuarios`, igual que los registradores. Este script es el puente
 * entre las dos cosas — el único punto donde nace una cuenta sin que otra
 * cuenta la haya creado.
 *
 * Es IDEMPOTENTE. Correrlo dos veces no cambia nada la segunda vez, y en
 * particular NO reescribe la contraseña de un admin que ya existe: un
 * despliegue automático que se repita no debe poder revertir en silencio un
 * cambio de contraseña hecho a propósito.
 *
 * Uso:
 *   ADMIN_USER=... ADMIN_PASSWORD=... node scripts/crear-admin.js
 *
 * o, con esas variables ya en el .env:
 *   npm run crear-admin
 */

const bcrypt = require('bcryptjs');
const { randomUUID } = require('crypto');
const pool = require('../src/db/pool');

const COSTO_BCRYPT = 12;
const LARGO_MINIMO_PASSWORD = 12;

/**
 * Se leen de process.env y no de src/config.js a propósito: config valida lo
 * que el SERVIDOR necesita para arrancar, y el servidor ya no necesita saber
 * nada del admin. Estas dos variables son insumo de este script y de nadie más.
 */
function leerCredenciales() {
    const usuario = String(process.env.ADMIN_USER || '').trim();
    const password = String(process.env.ADMIN_PASSWORD || '');

    const problemas = [];

    if (!usuario) {
        problemas.push('ADMIN_USER está vacío o no está definido.');
    }
    if (!password) {
        problemas.push('ADMIN_PASSWORD está vacío o no está definido.');
    } else if (password.length < LARGO_MINIMO_PASSWORD) {
        problemas.push(
            `ADMIN_PASSWORD debe tener al menos ${LARGO_MINIMO_PASSWORD} caracteres ` +
                `(tiene ${password.length}).`
        );
    }

    if (problemas.length > 0) {
        console.error('No se puede crear el administrador:\n');
        problemas.forEach((p) => console.error(`  - ${p}`));
        console.error(
            '\nDefinirlas en backend/.env o pasarlas en la línea de comandos:\n' +
                '  ADMIN_USER=admin ADMIN_PASSWORD=... node scripts/crear-admin.js\n'
        );
        process.exit(1);
    }

    return { usuario, password };
}

async function main() {
    const { usuario, password } = leerCredenciales();

    // La comparación es sin distinguir mayúsculas para coincidir con el índice
    // único de la tabla. Preguntar con `= $1` a secas dejaría pasar un INSERT
    // que después la base rechaza, y el error sería mucho menos claro.
    const yaExiste = await pool.query(
        'SELECT id, rol, activo FROM usuarios WHERE lower(usuario) = lower($1)',
        [usuario]
    );

    if (yaExiste.rows.length > 0) {
        const fila = yaExiste.rows[0];
        console.log(`El usuario "${usuario}" ya existe. No se modificó nada.`);
        console.log(`  id:     ${fila.id}`);
        console.log(`  rol:    ${fila.rol}`);
        console.log(`  activo: ${fila.activo}`);
        console.log(
            '\nEste script solo crea. Para reemplazar esta cuenta hay que borrar la\n' +
                'fila de `usuarios` y volver a correrlo.'
        );
        return;
    }

    const passwordHash = await bcrypt.hash(password, COSTO_BCRYPT);

    // creado_por queda NULL: este admin no lo creó nadie desde adentro del
    // sistema. Es el único caso en toda la tabla donde eso es legítimo.
    const creado = await pool.query(
        `INSERT INTO usuarios (id, usuario, password_hash, rol, activo, creado_por)
         VALUES ($1, $2, $3, 'admin', true, NULL)
         RETURNING id, usuario, rol, creado_en`,
        [randomUUID(), usuario, passwordHash]
    );

    const admin = creado.rows[0];
    console.log('Administrador creado.');
    console.log(`  id:      ${admin.id}`);
    console.log(`  usuario: ${admin.usuario}`);
    console.log(`  rol:     ${admin.rol}`);
    console.log(`  creado:  ${admin.creado_en.toISOString()}`);
    console.log('\nCon esta cuenta ya se pueden crear registradores desde la aplicación.');
}

main()
    .catch((error) => {
        // 42P01: la tabla no existe. Es el error más probable al desplegar y
        // el mensaje crudo de Postgres no sugiere qué hacer.
        if (error.code === '42P01') {
            console.error(
                'La tabla `usuarios` no existe todavía.\n\n' +
                    'El esquema se aplica solo cuando el volumen de Postgres está vacío.\n' +
                    'Si la base ya tenía datos, hay que recrear el volumen:\n\n' +
                    '  docker compose --env-file backend/.env down -v\n' +
                    '  docker compose --env-file backend/.env up -d --build\n'
            );
            process.exit(1);
        }
        console.error(error);
        process.exit(1);
    })
    .finally(() => pool.end());
