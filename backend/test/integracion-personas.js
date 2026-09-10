/**
 * Verificación de integración del padrón: altas, merge por campo, conflictos,
 * bajas y sincronización.
 *
 * Es la prueba que justifica el diseño. Los tests unitarios comprueban que la
 * decisión de merge sea correcta; esta comprueba que el servidor la aplique
 * dentro de una transacción, contra una base de verdad, con dos operadores
 * pisándose.
 *
 * Requiere un Postgres con el esquema aplicado y un admin creado:
 *
 *   docker run -d --name padron_test -p 5433:5432 \
 *     -e POSTGRES_PASSWORD=prueba -e POSTGRES_DB=padron postgres:16-alpine
 *   docker exec -i padron_test psql -U postgres -d padron < db/init/01_esquema.sql
 *   ADMIN_USER=admin ADMIN_PASSWORD=claveDeIntegracion123 npm run crear-admin
 *   npm run test:personas
 */

const { spawn } = require('child_process');
const path = require('path');
const { randomUUID } = require('crypto');

const RAIZ = path.join(__dirname, '..');
const PUERTO = Number(process.env.PUERTO_PRUEBA || 3998);
const BASE = `http://127.0.0.1:${PUERTO}`;
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
        /* sin cuerpo */
    }
    return { estado: respuesta.status, datos };
}

async function esperarServidor() {
    for (let i = 0; i < 200; i++) {
        try {
            if ((await fetch(`${BASE}/health`)).ok) return true;
        } catch {
            /* todavía no levantó */
        }
        await new Promise((r) => setTimeout(r, 50));
    }
    return false;
}

const personaBase = (extra = {}) => ({
    id: randomUUID(),
    tipo_documento: 'CC',
    numero_documento: String(Math.floor(Math.random() * 1e9)),
    primer_nombre: 'Rosa',
    primer_apellido: 'Perez',
    fecha_nacimiento: '1960-05-01',
    sexo: 'F',
    ...extra,
});

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

        // --- preparación: admin y dos registradores ------------------------
        const tokenAdmin = (
            await pedir('POST', '/api/auth/login-operador', {
                cuerpo: { usuario: 'admin', password: PASSWORD_ADMIN },
            })
        ).datos.token;

        for (const usuario of ['campo1', 'campo2']) {
            await pedir('POST', '/api/usuarios', {
                token: tokenAdmin,
                cuerpo: { usuario, password: 'claveDeRegistrador1' },
            });
        }
        const tokenReg1 = (
            await pedir('POST', '/api/auth/login-operador', {
                cuerpo: { usuario: 'campo1', password: 'claveDeRegistrador1' },
            })
        ).datos.token;
        const tokenReg2 = (
            await pedir('POST', '/api/auth/login-operador', {
                cuerpo: { usuario: 'campo2', password: 'claveDeRegistrador1' },
            })
        ).datos.token;

        // ==================================================================
        console.log('\n== alta ==');
        const rosa = personaBase();
        let r = await pedir('POST', '/api/personas', { token: tokenReg1, cuerpo: rosa });
        verificar('el registrador da de alta -> 201', r.estado === 201, `(dio ${r.estado})`);
        verificar('nace en versión 1', r.datos.persona.version === 1);
        verificar('queda quién la registró', Boolean(r.datos.persona.creado_por));

        r = await pedir('POST', '/api/personas', { token: tokenReg1, cuerpo: rosa });
        verificar('reintento de la outbox -> 200 idempotente', r.estado === 200, `(dio ${r.estado})`);
        verificar('lo informa como reintento', r.datos.reintento === true);

        r = await pedir('POST', '/api/personas', {
            token: tokenReg1,
            cuerpo: personaBase({ primer_nombre: '' }),
        });
        verificar('sin nombre -> 400', r.estado === 400, `(dio ${r.estado})`);

        r = await pedir('POST', '/api/personas', {
            token: tokenReg1,
            cuerpo: { ...personaBase(), tipo_documento: 'TI', numero_documento: rosa.numero_documento },
        });
        verificar('mismo número con otro tipo de documento -> 201', r.estado === 201, `(dio ${r.estado})`);

        // ==================================================================
        console.log('\n== alta duplicada: el caso que perdía datos ==');
        const duplicada = personaBase({
            numero_documento: rosa.numero_documento,
            primer_nombre: 'Rosa',
            telefono: '3009998877',
        });
        r = await pedir('POST', '/api/personas', { token: tokenReg2, cuerpo: duplicada });
        verificar('otro registrador, mismo documento -> 409', r.estado === 409, `(dio ${r.estado})`);
        verificar('con código ALTA_DUPLICADA', r.datos.codigo === 'ALTA_DUPLICADA');
        verificar('devuelve el id del conflicto', Boolean(r.datos.conflicto_id));
        verificar('apunta a la persona que ya existía', Boolean(r.datos.persona_existente_id));
        const idConflictoAlta = r.datos.conflicto_id;

        // ==================================================================
        console.log('\n== edición y merge ==');
        const idRosa = rosa.id;

        r = await pedir('PUT', `/api/personas/${idRosa}`, {
            token: tokenReg1,
            cuerpo: { version_base: 1, cambios: { telefono: '3001112233' } },
        });
        verificar('edición sobre la versión actual -> 200', r.estado === 200, `(dio ${r.estado})`);
        verificar('sube a versión 2', r.datos.persona.version === 2);

        r = await pedir('PUT', `/api/personas/${idRosa}`, {
            token: tokenReg1,
            cuerpo: { version_base: 2, cambios: { telefono: '3001112233' } },
        });
        verificar('reenviar el mismo valor no es un cambio', r.datos.sin_cambios === true);

        // El admin corrige el apellido. El registrador todavía tiene v2.
        r = await pedir('PUT', `/api/personas/${idRosa}`, {
            token: tokenAdmin,
            cuerpo: { version_base: 2, cambios: { primer_apellido: 'Perez Gomez' } },
        });
        verificar('el admin edita el apellido -> 200', r.estado === 200, `(dio ${r.estado})`);
        const versionTrasAdmin = r.datos.persona.version;

        // Ahora el registrador manda un cambio de OTRO campo, desde v2.
        r = await pedir('PUT', `/api/personas/${idRosa}`, {
            token: tokenReg1,
            cuerpo: { version_base: 2, cambios: { telefono: '3005554444' } },
        });
        verificar('campos distintos a la vez -> 200 MERGE', r.estado === 200, `(dio ${r.estado})`);
        verificar('lo informa como mergeado', r.datos.mergeado === true);
        verificar(
            'sobrevive el cambio del admin',
            r.datos.persona.primer_apellido === 'Perez Gomez',
            `(quedó ${r.datos.persona && r.datos.persona.primer_apellido})`
        );
        verificar(
            'sobrevive el cambio del registrador',
            r.datos.persona.telefono === '3005554444'
        );

        // ==================================================================
        console.log('\n== conflicto real: el mismo campo ==');
        const versionActual = r.datos.persona.version;
        await pedir('PUT', `/api/personas/${idRosa}`, {
            token: tokenAdmin,
            cuerpo: { version_base: versionActual, cambios: { telefono: '3007776666' } },
        });

        r = await pedir('PUT', `/api/personas/${idRosa}`, {
            token: tokenReg1,
            cuerpo: { version_base: versionActual, cambios: { telefono: '3001234567' } },
        });
        verificar('el mismo campo desde la misma base -> 409', r.estado === 409, `(dio ${r.estado})`);
        verificar('con código EDICION_CONCURRENTE', r.datos.codigo === 'EDICION_CONCURRENTE');
        verificar('dice qué campo chocó', (r.datos.campos_en_conflicto || []).includes('telefono'));
        verificar('devuelve el id del conflicto', Boolean(r.datos.conflicto_id));
        verificar('adjunta el estado del servidor', Boolean(r.datos.persona_servidor));
        verificar(
            'el valor del admin no se pisó',
            r.datos.persona_servidor.telefono === '3007776666'
        );

        r = await pedir('PUT', `/api/personas/${idRosa}`, {
            token: tokenReg1,
            cuerpo: { version_base: 999, cambios: { telefono: '3000000000' } },
        });
        verificar('versión que nunca existió -> 409', r.estado === 409, `(dio ${r.estado})`);
        verificar('con código VERSION_INVALIDA', r.datos.codigo === 'VERSION_INVALIDA');

        // ==================================================================
        console.log('\n== auditoría ==');
        r = await pedir('GET', `/api/personas/${idRosa}/historial`, { token: tokenAdmin });
        verificar('el admin lee el historial -> 200', r.estado === 200, `(dio ${r.estado})`);
        verificar('tiene más de 3 entradas (ya no se recorta)', r.datos.length > 3, `(hay ${r.datos.length})`);
        verificar(
            'cada entrada dice quién la hizo',
            r.datos.every((h) => Boolean(h.realizado_por))
        );
        verificar(
            'las ediciones dicen qué campo cambió',
            r.datos.filter((h) => h.operacion === 'UPDATE').every((h) => Boolean(h.campo))
        );
        verificar(
            'guarda el alta',
            r.datos.some((h) => h.operacion === 'CREATE')
        );
        verificar(
            'hay entradas de dos autores distintos',
            new Set(r.datos.map((h) => h.realizado_por)).size >= 2
        );

        // ==================================================================
        console.log('\n== límites del registrador ==');
        const deReg2 = personaBase();
        await pedir('POST', '/api/personas', { token: tokenReg2, cuerpo: deReg2 });

        r = await pedir('PUT', `/api/personas/${deReg2.id}`, {
            token: tokenReg1,
            cuerpo: { version_base: 1, cambios: { telefono: '3001112233' } },
        });
        verificar('editar lo de otro registrador -> 403', r.estado === 403, `(dio ${r.estado})`);

        r = await pedir('GET', `/api/personas/${deReg2.id}`, { token: tokenReg1 });
        verificar('leer por id lo de otro registrador -> 404', r.estado === 404, `(dio ${r.estado})`);

        r = await pedir('GET', '/api/personas', { token: tokenReg1 });
        verificar('el registrador no lista todo el padrón -> 403', r.estado === 403, `(dio ${r.estado})`);

        r = await pedir('GET', `/api/personas/${idRosa}/historial`, { token: tokenReg1 });
        verificar('el registrador no lee historiales -> 403', r.estado === 403, `(dio ${r.estado})`);

        // ==================================================================
        console.log('\n== sincronización ==');
        r = await pedir('GET', '/api/personas/sync?desde=0', { token: tokenReg1 });
        verificar('el registrador sincroniza -> 200', r.estado === 200, `(dio ${r.estado})`);
        const idsReg1 = r.datos.personas.map((p) => p.id);
        verificar('solo baja lo suyo', idsReg1.includes(idRosa) && !idsReg1.includes(deReg2.id));
        verificar('devuelve un cursor', typeof r.datos.cursor === 'number');
        const cursorReg1 = r.datos.cursor;

        r = await pedir('GET', `/api/personas/sync?desde=${cursorReg1}`, { token: tokenReg1 });
        verificar('desde el cursor no repite nada', r.datos.personas.length === 0);

        r = await pedir('GET', '/api/personas/sync?desde=0', { token: tokenAdmin });
        const idsAdmin = r.datos.personas.map((p) => p.id);
        verificar('el admin baja todo', idsAdmin.includes(idRosa) && idsAdmin.includes(deReg2.id));

        // ==================================================================
        console.log('\n== baja ==');
        const actual = (await pedir('GET', `/api/personas/${idRosa}`, { token: tokenAdmin })).datos;

        r = await pedir('DELETE', `/api/personas/${idRosa}`, {
            token: tokenReg1,
            cuerpo: { version_base: 1 },
        });
        verificar('baja con versión vieja -> 409', r.estado === 409, `(dio ${r.estado})`);

        r = await pedir('DELETE', `/api/personas/${idRosa}`, {
            token: tokenReg1,
            cuerpo: { version_base: actual.version },
        });
        verificar('baja con la versión correcta -> 200', r.estado === 200, `(dio ${r.estado})`);

        r = await pedir('DELETE', `/api/personas/${idRosa}`, {
            token: tokenReg1,
            cuerpo: { version_base: actual.version },
        });
        verificar('reintento sobre algo ya dado de baja -> 200', r.estado === 200, `(dio ${r.estado})`);

        r = await pedir('GET', `/api/personas/sync?desde=${cursorReg1}`, { token: tokenReg1 });
        const dadaDeBaja = r.datos.personas.find((p) => p.id === idRosa);
        verificar('la baja viaja por sync', Boolean(dadaDeBaja));
        verificar('con deleted_at para que el teléfono la borre', Boolean(dadaDeBaja && dadaDeBaja.deleted_at));

        r = await pedir('POST', '/api/personas', {
            token: tokenReg2,
            cuerpo: personaBase({ numero_documento: rosa.numero_documento }),
        });
        verificar('la baja libera el documento -> 201', r.estado === 201, `(dio ${r.estado})`);

        // ==================================================================
        console.log('\n== el rol usuario ==');
        const suya = personaBase({ numero_documento: '77777777' });
        await pedir('POST', '/api/personas', { token: tokenReg1, cuerpo: suya });

        r = await pedir('POST', '/api/auth/login-persona', {
            cuerpo: { tipo_documento: 'CC', numero_documento: '77777777' },
        });
        verificar('la persona entra con su documento -> 200', r.estado === 200, `(dio ${r.estado})`);
        const tokenPersona = r.datos.token;

        r = await pedir('GET', `/api/personas/${suya.id}`, { token: tokenPersona });
        verificar('lee su propio registro -> 200', r.estado === 200, `(dio ${r.estado})`);

        r = await pedir('GET', `/api/personas/${deReg2.id}`, { token: tokenPersona });
        verificar('no lee el de otra persona -> 403', r.estado === 403, `(dio ${r.estado})`);

        r = await pedir('PUT', `/api/personas/${suya.id}`, {
            token: tokenPersona,
            cuerpo: { version_base: 1, cambios: { telefono: '3001112233' } },
        });
        verificar('no puede editar ni lo suyo -> 403', r.estado === 403, `(dio ${r.estado})`);

        r = await pedir('POST', '/api/personas', { token: tokenPersona, cuerpo: personaBase() });
        verificar('no puede registrar a nadie -> 403', r.estado === 403, `(dio ${r.estado})`);

        // ==================================================================
        console.log('\n== los conflictos quedaron guardados ==');
        verificar('el conflicto del alta duplicada tiene id', Boolean(idConflictoAlta));
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
