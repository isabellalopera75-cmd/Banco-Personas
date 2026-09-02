// Genera el hash de una contraseña de administrador elegida por vos.
//
// Uso:
//   node scripts/cambiar-password-admin.js
//
// Pide la contraseña sin mostrarla en pantalla, a propósito: pasarla como
// argumento del comando la dejaría escrita en el historial de la terminal y
// visible para cualquiera que liste los procesos del sistema.
//
// La contraseña en sí nunca se guarda ni se imprime: solo se muestra su hash,
// que es lo único que va al archivo .env.

const readline = require('readline');
const bcrypt = require('bcryptjs');

const LARGO_MINIMO = 12;
const COSTO_BCRYPT = 12;

// Solo se puede ocultar lo tecleado cuando hay una terminal real detrás.
const esTerminal = Boolean(process.stdin.isTTY);

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: esTerminal,
});

let oculto = false;
if (esTerminal) {
    // El prompt se escribe directo a stdout, así que esto solo silencia
    // el eco de las teclas.
    rl._writeToOutput = (texto) => {
        if (!oculto) rl.output.write(texto);
    };
}

// Las líneas se encolan a medida que llegan. Encadenar rl.question() fallaba
// con la entrada redirigida: readline consume todo el buffer de una vez y la
// segunda pregunta se quedaba esperando para siempre.
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

function preguntar(texto) {
    process.stdout.write(texto);

    return new Promise((resolve) => {
        const entregar = (valor) => {
            oculto = false;
            if (esTerminal) process.stdout.write('\n');
            resolve(valor === null ? null : valor.trim());
        };

        if (lineasPendientes.length > 0) {
            entregar(lineasPendientes.shift());
        } else if (entradaCerrada) {
            entregar(null);
        } else {
            oculto = esTerminal;
            preguntasPendientes.push(entregar);
        }
    });
}

function abortar(mensaje) {
    rl.close();
    console.error(mensaje);
    process.exitCode = 1;
}

(async () => {
    const password = await preguntar('Nueva contraseña de administrador: ');
    const repetida = await preguntar('Repetir contraseña: ');
    rl.close();

    if (password === null || repetida === null) {
        return abortar('Entrada incompleta. No se generó nada.');
    }
    if (password !== repetida) {
        return abortar('Las contraseñas no coinciden. No se generó nada.');
    }
    // El administrador ve y edita los datos de todos los participantes, así
    // que se le exige más que a una cuenta común.
    if (password.length < LARGO_MINIMO) {
        return abortar(
            `La contraseña de administrador debe tener al menos ${LARGO_MINIMO} caracteres.`
        );
    }

    const hash = bcrypt.hashSync(password, COSTO_BCRYPT);

    console.log('Reemplazar esta línea en el archivo .env:\n');
    console.log(`ADMIN_PASSWORD_HASH='${hash}'`);
    console.log('\nDespués reiniciar el servidor para que tome el cambio.');
})();
