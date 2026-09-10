/**
 * Verificación de integración de autenticación y usuarios.
 *
 * No es un test unitario: levanta el servidor de verdad y le pega por HTTP.
 * Comprueba lo que los tests unitarios no pueden — que las rutas estén
 * cableadas al middleware correcto, que la base aplique sus restricciones y
 * que un rol no pueda hacer lo del otro.
 *
 * Necesita un Postgres con el esquema aplicado y un admin ya creado:
 *
 *   docker run -d --name padron_test -p 5433:5432  *     -e POSTGRES_PASSWORD=prueba -e POSTGRES_DB=padron postgres:16-alpine
 *   docker exec -i padron_test psql -U postgres -d padron < db/init/01_esquema.sql
 *   ADMIN_USER=admin ADMIN_PASSWORD=claveDeIntegracion123 npm run crear-admin
 *   npm run test:integracion
 *
 * Deja la base vacía al empezar: se ejecuta sobre datos descartables, nunca
 * contra una base con información real.
 */

const { spawn } = require('child_process');
const path = require('path');

const RAIZ = path.join(__dirname, '..');
const PUERTO = Number(process.env.PUERTO_PRUEBA || 3999);
const BASE = `http://127.0.0.1:${PUERTO}`;

// El admin tiene que existir antes de correr esto, con esta contraseña.
const PASSWORD_ADMIN = process.env.ADMIN_PASSWORD || 'claveDeIntegracion123';

const ENTORNO = {
    ...process.env,
    DB_USER: process.env.DB_USER || 'postgres',
    DB_PASSWORD: process.env.DB_PASSWORD || 'prueba',
    DB_HOST: process.env.DB_HOST || '127.0.0.1',
    DB_PORT: process.env.DB_PORT || '5433',
    DB_NAME: process.env.DB_NAME || 'padron',
    JWT_SECRET: 'secreto-de-integracion',
    JWT_EXPIRES_IN: '1h',
    PORT: String(PUERTO),
};

let pasadas = 0;
let falladas = 0;

function verificar(descripcion, condicion, detalle = '') {
    if (condicion) {
        console.log(`  OK    ${descripcion}`);
        pasadas++;
    } else {
        console.log(`  FALLA ${descripcion} ${detalle}`);
        falladas++;
    }
}

async function pedir(metodo, ruta, { token, cuerpo } = {}) {
    const cabeceras = { 'Content-Type': 'application/json' };
    if (token) cabeceras.Authorization = `Bearer ${token}`;

    const respuesta = await fetch(`${BASE}${ruta}`, {
        method: metodo,
        headers: cabeceras,
        body: cuerpo ? JSON.stringify(cuerpo) : undefined,
    });

    let datos = null;
    try {
        datos = await respuesta.json();
    } catch {
        /* 204 y similares no traen cuerpo */
    }
    return { estado: respuesta.status, datos };
}

async function esperarServidor() {
    for (let i = 0; i < 200; i++) {
        try {
            const r = await fetch(`${BASE}/health`);
            if (r.ok) return true;
        } catch {
            /* todavía no levantó */
        }
        // Sin esta pausa los 200 intentos se agotan en milisegundos, mucho
        // antes de que Node termine de levantar el servidor.
        await new Promise((resolver) => setTimeout(resolver, 50));
    }
    return false;
}

async function main() {
    const servidor = spawn(process.execPath, [path.join(RAIZ, 'src/server.js')], {
        env: ENTORNO,
        cwd: RAIZ,
        stdio: ['ignore', 'pipe', 'pipe'],
    });
    servidor.stderr.on('data', (d) => process.stderr.write(`[servidor] ${d}`));

    try {
        if (!(await esperarServidor())) {
            console.log('  FALLA el servidor no levantó');
            falladas++;
            return;
        }

        console.log('\n== login de operador ==');
        let r = await pedir('POST', '/api/auth/login-operador', {
            cuerpo: { usuario: 'admin', password: 'contraseñaEquivocada' },
        });
        verificar('contraseña incorrecta -> 401', r.estado === 401, `(dio ${r.estado})`);

        r = await pedir('POST', '/api/auth/login-operador', {
            cuerpo: { usuario: 'noExiste', password: 'loQueSea12345' },
        });
        verificar('usuario inexistente -> 401', r.estado === 401, `(dio ${r.estado})`);
        verificar(
            'mismo mensaje para ambos casos (no permite enumerar cuentas)',
            r.datos.error === 'Usuario o contraseña incorrectos'
        );

        r = await pedir('POST', '/api/auth/login-operador', {
            cuerpo: { usuario: 'ADMIN', password: PASSWORD_ADMIN },
        });
        verificar('login correcto, usuario en mayúsculas -> 200', r.estado === 200, `(dio ${r.estado})`);
        verificar('devuelve rol admin', r.datos.rol === 'admin');
        verificar('no filtra el hash', JSON.stringify(r.datos).includes('password_hash') === false);
        const tokenAdmin = r.datos.token;

        console.log('\n== protección de /api/usuarios ==');
        r = await pedir('GET', '/api/usuarios');
        verificar('sin token -> 401', r.estado === 401, `(dio ${r.estado})`);

        r = await pedir('GET', '/api/usuarios', { token: 'basura' });
        verificar('token inválido -> 401', r.estado === 401, `(dio ${r.estado})`);

        r = await pedir('GET', '/api/usuarios', { token: tokenAdmin });
        verificar('admin -> 200', r.estado === 200, `(dio ${r.estado})`);

        console.log('\n== alta de registradores ==');
        r = await pedir('POST', '/api/usuarios', {
            token: tokenAdmin,
            cuerpo: { usuario: 'reg1', password: 'corta' },
        });
        verificar('contraseña corta -> 400', r.estado === 400, `(dio ${r.estado})`);

        r = await pedir('POST', '/api/usuarios', {
            token: tokenAdmin,
            cuerpo: { usuario: 'reg1', password: 'claveDeRegistrador1' },
        });
        verificar('registrador creado -> 201', r.estado === 201, `(dio ${r.estado})`);
        verificar('nace con rol registrador', r.datos.rol === 'registrador');
        verificar('queda registrado quién lo creó', Boolean(r.datos.creado_por));
        const idReg1 = r.datos.id;

        r = await pedir('POST', '/api/usuarios', {
            token: tokenAdmin,
            cuerpo: { usuario: 'REG1', password: 'otraClaveLarga123' },
        });
        verificar('mismo nombre con otra capitalización -> 409', r.estado === 409, `(dio ${r.estado})`);

        r = await pedir('POST', '/api/usuarios', {
            token: tokenAdmin,
            cuerpo: { usuario: 'reg2', password: 'claveDeRegistrador2' },
        });
        verificar('varios registradores permitidos -> 201', r.estado === 201, `(dio ${r.estado})`);

        console.log('\n== el registrador no puede gestionar cuentas ==');
        r = await pedir('POST', '/api/auth/login-operador', {
            cuerpo: { usuario: 'reg1', password: 'claveDeRegistrador1' },
        });
        verificar('el registrador entra -> 200', r.estado === 200, `(dio ${r.estado})`);
        const tokenReg = r.datos.token;

        r = await pedir('GET', '/api/usuarios', { token: tokenReg });
        verificar('registrador listando usuarios -> 403', r.estado === 403, `(dio ${r.estado})`);

        r = await pedir('POST', '/api/usuarios', {
            token: tokenReg,
            cuerpo: { usuario: 'colado', password: 'claveLarguisima123' },
        });
        verificar('registrador creando cuentas -> 403', r.estado === 403, `(dio ${r.estado})`);

        console.log('\n== desactivación ==');
        r = await pedir('PATCH', `/api/usuarios/${idReg1}`, {
            token: tokenAdmin,
            cuerpo: { activo: false },
        });
        verificar('desactivar registrador -> 200', r.estado === 200, `(dio ${r.estado})`);
        verificar('queda inactivo', r.datos.activo === false);

        r = await pedir('POST', '/api/auth/login-operador', {
            cuerpo: { usuario: 'reg1', password: 'claveDeRegistrador1' },
        });
        verificar('el desactivado ya no entra -> 403', r.estado === 403, `(dio ${r.estado})`);

        const usuarios = (await pedir('GET', '/api/usuarios', { token: tokenAdmin })).datos;
        const idAdmin = usuarios.find((u) => u.rol === 'admin').id;

        r = await pedir('PATCH', `/api/usuarios/${idAdmin}`, {
            token: tokenAdmin,
            cuerpo: { activo: false },
        });
        verificar('desactivar al único admin -> 409', r.estado === 409, `(dio ${r.estado})`);

        r = await pedir('GET', '/api/usuarios', { token: tokenAdmin });
        verificar(
            'el admin sigue activo tras el intento (rollback funcionó)',
            r.estado === 200 && r.datos.find((u) => u.id === idAdmin).activo === true
        );

        console.log('\n== login de persona ==');
        r = await pedir('POST', '/api/auth/login-persona', {
            cuerpo: { tipo_documento: 'CC', numero_documento: '000000' },
        });
        verificar('documento inexistente -> 401', r.estado === 401, `(dio ${r.estado})`);

        r = await pedir('POST', '/api/auth/login-persona', { cuerpo: { tipo_documento: 'CC' } });
        verificar('falta el número -> 400', r.estado === 400, `(dio ${r.estado})`);

        console.log('\n== limitador de intentos ==');
        let vioBloqueo = false;
        for (let i = 0; i < 8; i++) {
            const intento = await pedir('POST', '/api/auth/login-persona', {
                cuerpo: { tipo_documento: 'CC', numero_documento: `99${i}` },
            });
            if (intento.estado === 429) vioBloqueo = true;
        }
        verificar('un barrido de documentos termina en 429', vioBloqueo);
    } finally {
        servidor.kill();
    }

    console.log(`\n===== ${pasadas} pasaron, ${falladas} fallaron =====`);
    process.exit(falladas === 0 ? 0 : 1);
}

main().catch((e) => {
    console.error(e);
    process.exit(1);
});
