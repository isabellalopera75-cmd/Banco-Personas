/**
 * Verificación de integración de la cola de conflictos.
 *
 * Comprueba la salida de los datos que el sistema anterior tiraba: que el
 * admin pueda ver los dos registros enfrentados, quedarse con lo mejor de
 * cada uno, separarlos si eran personas distintas, o descartar dejando
 * escrito por qué.
 *
 * Requiere el mismo Postgres de prueba que los otros dos arneses.
 */

const { spawn } = require('child_process');
const path = require('path');
const { randomUUID } = require('crypto');

const RAIZ = path.join(__dirname, '..');
const PUERTO = Number(process.env.PUERTO_PRUEBA || 3997);
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
        const entrar = async (usuario) =>
            (
                await pedir('POST', '/api/auth/login-operador', {
                    cuerpo: { usuario, password: 'claveDeRegistrador1' },
                })
            ).datos.token;
        const tokenReg1 = await entrar('campo1');
        const tokenReg2 = await entrar('campo2');

        // ==================================================================
        console.log('\n== preparación: se generan los dos tipos de conflicto ==');
        const rosa = personaBase({ telefono: null });
        await pedir('POST', '/api/personas', { token: tokenReg1, cuerpo: rosa });

        // campo2 registró a la misma persona sin conexión, con más datos.
        const suIntento = personaBase({
            numero_documento: rosa.numero_documento,
            primer_nombre: 'Rosa',
            segundo_nombre: 'Elena',
            telefono: '3009998877',
        });
        let r = await pedir('POST', '/api/personas', { token: tokenReg2, cuerpo: suIntento });
        verificar('el alta duplicada genera conflicto', r.datos.codigo === 'ALTA_DUPLICADA');
        const conflictoAlta = r.datos.conflicto_id;

        // Edición concurrente sobre el mismo campo.
        r = await pedir('PUT', `/api/personas/${rosa.id}`, {
            token: tokenReg1,
            cuerpo: { version_base: 1, cambios: { telefono: '3001111111' } },
        });
        const v2 = r.datos.persona.version;
        await pedir('PUT', `/api/personas/${rosa.id}`, {
            token: tokenAdmin,
            cuerpo: { version_base: v2, cambios: { telefono: '3002222222' } },
        });
        r = await pedir('PUT', `/api/personas/${rosa.id}`, {
            token: tokenReg1,
            cuerpo: { version_base: v2, cambios: { telefono: '3003333333' } },
        });
        verificar('la edición concurrente genera conflicto', r.datos.codigo === 'EDICION_CONCURRENTE');
        const conflictoEdicion = r.datos.conflicto_id;

        // ==================================================================
        console.log('\n== la cola del admin ==');
        r = await pedir('GET', '/api/conflictos', { token: tokenAdmin });
        verificar('el admin ve la cola -> 200', r.estado === 200, `(dio ${r.estado})`);
        verificar('están los dos conflictos', r.datos.conflictos.length === 2, `(hay ${r.datos.conflictos.length})`);

        const enCola = r.datos.conflictos.find((c) => c.id === conflictoAlta);
        verificar('trae la persona con la que chocó', Boolean(enCola.persona_existente));
        verificar('trae el intento completo', enCola.datos_enviados.telefono === '3009998877');
        verificar('dice quién lo mandó por nombre', enCola.enviado_por === 'campo2');

        r = await pedir('GET', '/api/conflictos', { token: tokenReg1 });
        verificar('el registrador no ve la cola -> 403', r.estado === 403, `(dio ${r.estado})`);

        r = await pedir('GET', '/api/conflictos/mios', { token: tokenReg2 });
        verificar('campo2 ve lo suyo -> 200', r.estado === 200, `(dio ${r.estado})`);
        verificar('y es solo lo suyo', r.datos.conflictos.length === 1, `(ve ${r.datos.conflictos.length})`);
        verificar('en estado PENDIENTE', r.datos.conflictos[0].estado === 'PENDIENTE');

        // ==================================================================
        console.log('\n== fusionar: quedarse con lo mejor de cada uno ==');
        r = await pedir('POST', `/api/conflictos/${conflictoAlta}/fusionar`, {
            token: tokenAdmin,
            cuerpo: {
                campos: { telefono: '3009998877', segundo_nombre: 'Elena' },
                nota: 'Es la misma persona; campo2 traía teléfono y segundo nombre',
            },
        });
        verificar('el admin fusiona -> 200', r.estado === 200, `(dio ${r.estado})`);
        verificar(
            'se aprovechó el teléfono que se habría perdido',
            r.datos.persona.telefono === '3009998877',
            `(quedó ${r.datos.persona && r.datos.persona.telefono})`
        );
        verificar('y el segundo nombre', r.datos.persona.segundo_nombre === 'Elena');
        verificar('informa qué campos aplicó', r.datos.campos_aplicados.length === 2);

        r = await pedir('POST', `/api/conflictos/${conflictoAlta}/fusionar`, {
            token: tokenAdmin,
            cuerpo: { campos: { telefono: '3000000000' } },
        });
        verificar('fusionar dos veces -> 409', r.estado === 409, `(dio ${r.estado})`);
        verificar('con código YA_RESUELTO', r.datos.codigo === 'YA_RESUELTO');

        r = await pedir('GET', '/api/conflictos/mios', { token: tokenReg2 });
        verificar('campo2 ve que se resolvió', r.datos.conflictos[0].estado === 'RESUELTO');
        verificar('con la nota del admin', Boolean(r.datos.conflictos[0].nota_resolucion));

        r = await pedir('GET', `/api/personas/${rosa.id}/historial`, { token: tokenAdmin });
        const deConflicto = r.datos.filter((h) => h.conflicto_id === conflictoAlta);
        verificar('el historial ata el cambio a su conflicto', deConflicto.length === 2, `(hay ${deConflicto.length})`);
        verificar(
            'y sigue diciendo qué campo cambió',
            deConflicto.every((h) => Boolean(h.campo))
        );

        // ==================================================================
        console.log('\n== descartar ==');
        r = await pedir('POST', `/api/conflictos/${conflictoEdicion}/descartar`, {
            token: tokenAdmin,
            cuerpo: { nota: 'ok' },
        });
        verificar('descartar sin explicar -> 400', r.estado === 400, `(dio ${r.estado})`);

        r = await pedir('POST', `/api/conflictos/${conflictoEdicion}/descartar`, {
            token: tokenAdmin,
            cuerpo: { nota: 'El teléfono correcto es el que cargó el admin, confirmado por la persona' },
        });
        verificar('descartar con motivo -> 200', r.estado === 200, `(dio ${r.estado})`);

        r = await pedir('GET', '/api/conflictos?estado=DESCARTADO', { token: tokenAdmin });
        const descartado = r.datos.conflictos.find((c) => c.id === conflictoEdicion);
        verificar('queda registrado como descartado', Boolean(descartado));
        verificar(
            'descartado NO es borrado: el intento sigue guardado',
            descartado.datos_enviados.telefono === '3003333333'
        );
        verificar('y dice quién lo descartó', descartado.resuelto_por === 'admin');

        // ==================================================================
        console.log('\n== crear como nueva: eran dos personas distintas ==');
        const juan = personaBase({ primer_nombre: 'Juan', primer_apellido: 'Gomez', sexo: 'M' });
        await pedir('POST', '/api/personas', { token: tokenReg1, cuerpo: juan });

        const tipeoMal = personaBase({
            numero_documento: juan.numero_documento,
            primer_nombre: 'Pedro',
            primer_apellido: 'Ramirez',
            sexo: 'M',
            telefono: '3005551234',
        });
        r = await pedir('POST', '/api/personas', { token: tokenReg2, cuerpo: tipeoMal });
        const conflictoTipeo = r.datos.conflicto_id;

        r = await pedir('POST', `/api/conflictos/${conflictoTipeo}/crear-como-nueva`, {
            token: tokenAdmin,
            cuerpo: { tipo_documento: 'CC', numero_documento: '4444555566' },
        });
        verificar('se crea como registro nuevo -> 201', r.estado === 201, `(dio ${r.estado})`);
        verificar('con el documento corregido', r.datos.persona.numero_documento === '4444555566');
        verificar('conserva los datos del intento', r.datos.persona.primer_nombre === 'Pedro');
        verificar(
            'reusa el id que había generado el teléfono',
            r.datos.persona.id === tipeoMal.id,
            '(si no, ese dispositivo se quedaría con un duplicado propio)'
        );

        r = await pedir('GET', `/api/personas/${tipeoMal.id}`, { token: tokenReg2 });
        verificar(
            'queda a nombre de quien lo registró, no del admin',
            r.estado === 200,
            `(campo2 debería poder verlo, dio ${r.estado})`
        );

        r = await pedir('POST', `/api/conflictos/${conflictoTipeo}/crear-como-nueva`, {
            token: tokenAdmin,
            cuerpo: { tipo_documento: 'CC', numero_documento: '9999888877' },
        });
        verificar('no se puede crear dos veces -> 409', r.estado === 409, `(dio ${r.estado})`);

        // ==================================================================
        console.log('\n== validaciones ==');
        const otra = personaBase();
        await pedir('POST', '/api/personas', { token: tokenReg1, cuerpo: otra });
        r = await pedir('PUT', `/api/personas/${otra.id}`, {
            token: tokenReg1,
            cuerpo: { version_base: 1, cambios: { telefono: '3001111111' } },
        });
        const vOtra = r.datos.persona.version;
        await pedir('PUT', `/api/personas/${otra.id}`, {
            token: tokenAdmin,
            cuerpo: { version_base: vOtra, cambios: { telefono: '3002222222' } },
        });
        r = await pedir('PUT', `/api/personas/${otra.id}`, {
            token: tokenReg1,
            cuerpo: { version_base: vOtra, cambios: { telefono: '3003333333' } },
        });
        const conflictoEdicion2 = r.datos.conflicto_id;

        r = await pedir('POST', `/api/conflictos/${conflictoEdicion2}/crear-como-nueva`, {
            token: tokenAdmin,
            cuerpo: { tipo_documento: 'CC', numero_documento: '1231231231' },
        });
        verificar('crear-como-nueva sobre una edición -> 400', r.estado === 400, `(dio ${r.estado})`);
        verificar('con código TIPO_INCORRECTO', r.datos.codigo === 'TIPO_INCORRECTO');

        r = await pedir('POST', `/api/conflictos/${randomUUID()}/fusionar`, {
            token: tokenAdmin,
            cuerpo: { campos: {} },
        });
        verificar('conflicto inexistente -> 404', r.estado === 404, `(dio ${r.estado})`);

        r = await pedir('POST', `/api/conflictos/${conflictoEdicion2}/fusionar`, {
            token: tokenReg1,
            cuerpo: { campos: {} },
        });
        verificar('el registrador no resuelve conflictos -> 403', r.estado === 403, `(dio ${r.estado})`);
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
