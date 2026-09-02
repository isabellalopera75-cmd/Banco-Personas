// Comprueba si una contraseña corresponde a un hash de administrador.
//
// Uso:
//   node scripts/verificar-password-admin.js
//
// Sirve para separar dos fallas que se ven idénticas desde la aplicación:
// una contraseña equivocada y un hash que no es el que uno cree. El servidor
// responde 401 en ambos casos, así que desde afuera no se distinguen.
//
// Todo ocurre en esta máquina: no consulta la API ni la base de datos, y no
// escribe nada. La contraseña se pide sin eco y no se guarda en ningún lado.

const readline = require('readline');
const bcrypt = require('bcryptjs');

const LARGO_HASH_BCRYPT = 60;

const esTerminal = Boolean(process.stdin.isTTY);

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: esTerminal,
});

let oculto = false;
if (esTerminal) {
    rl._writeToOutput = (texto) => {
        if (!oculto) rl.output.write(texto);
    };
}

// Mismo mecanismo que cambiar-password-admin.js: encadenar rl.question()
// falla con la entrada redirigida, porque readline consume todo el buffer
// de una vez y la segunda pregunta espera para siempre.
const lineasPendientes = [];
const preguntasPendientes = [];
let entradaCerrada = false;

rl.on('line', (linea) => {
    const pendiente = preguntasPendientes.shift();
    if (pendiente) pendiente(linea);
    else lineasPendientes.push(linea);
});

rl.on('close', () => {
    entradaCerrada = true;
    while (preguntasPendientes.length > 0) {
        preguntasPendientes.shift()(null);
    }
});

function preguntar(texto, { sinEco = false } = {}) {
    process.stdout.write(texto);

    return new Promise((resolve) => {
        const entregar = (valor) => {
            oculto = false;
            if (esTerminal && sinEco) process.stdout.write('\n');
            resolve(valor === null ? null : valor.trim());
        };

        if (lineasPendientes.length > 0) {
            entregar(lineasPendientes.shift());
        } else if (entradaCerrada) {
            entregar(null);
        } else {
            oculto = esTerminal && sinEco;
            preguntasPendientes.push(entregar);
        }
    });
}

(async () => {
    // El hash se pide con eco: no es el secreto, es el resultado público de
    // haber hasheado el secreto. Verlo al pegarlo evita el error más común,
    // que es pegarlo cortado.
    const hash = await preguntar('Hash de producción (ADMIN_PASSWORD_HASH): ');
    const password = await preguntar('Contraseña a probar: ', { sinEco: true });
    rl.close();

    if (!hash || !password) {
        console.error('\nEntrada incompleta. No se verificó nada.');
        process.exitCode = 1;
        return;
    }

    // Un hash cortado al pegar es el error más frecuente, y produce el mismo
    // "no coincide" que una contraseña equivocada. Conviene descartarlo antes.
    if (hash.length !== LARGO_HASH_BCRYPT) {
        console.error(
            `\nEse hash tiene ${hash.length} caracteres y bcrypt usa ${LARGO_HASH_BCRYPT}.` +
                '\nSe pegó incompleto o con comillas de más. Revisar antes de seguir.'
        );
        process.exitCode = 1;
        return;
    }

    if (bcrypt.compareSync(password, hash)) {
        console.log('\nCOINCIDE. Esa contraseña es la del hash que está en producción.');
        console.log('Si la aplicación igual la rechaza, el problema está en el campo');
        console.log('de usuario: tiene que decir exactamente lo que vale ADMIN_USER.');
    } else {
        console.log('\nNO COINCIDE. Esa contraseña no corresponde a ese hash.');
        console.log('Generar una nueva con scripts/cambiar-password-admin.js, pegar el');
        console.log('hash en la pestaña Environment y volver a desplegar.');
    }
})();
