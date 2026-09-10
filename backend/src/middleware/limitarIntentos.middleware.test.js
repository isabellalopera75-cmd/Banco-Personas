const test = require('node:test');
const assert = require('node:assert/strict');

const { crearLimitador } = require('./limitarIntentos.middleware');

function crearRes() {
    return {
        codigo: null,
        cuerpo: null,
        cabeceras: {},
        status(codigo) {
            this.codigo = codigo;
            return this;
        },
        json(cuerpo) {
            this.cuerpo = cuerpo;
            return this;
        },
        set(clave, valor) {
            this.cabeceras[clave] = valor;
            return this;
        },
    };
}

function crearNext() {
    const next = () => {
        next.llamado = true;
    };
    next.llamado = false;
    return next;
}

const req = (ip) => ({ ip, headers: {}, body: {} });

test('deja pasar mientras no se supere el máximo', () => {
    const limitador = crearLimitador({ maximo: 3, ventanaMs: 1000 });

    for (let i = 0; i < 3; i++) {
        const res = crearRes();
        const next = crearNext();
        limitador(req('1.1.1.1'), res, next);
        assert.equal(next.llamado, true, `el intento ${i + 1} debería pasar`);
    }
});

test('bloquea con 429 al superar el máximo', () => {
    const limitador = crearLimitador({ maximo: 2, ventanaMs: 1000 });

    limitador(req('2.2.2.2'), crearRes(), crearNext());
    limitador(req('2.2.2.2'), crearRes(), crearNext());

    const res = crearRes();
    const next = crearNext();
    limitador(req('2.2.2.2'), res, next);

    assert.equal(res.codigo, 429);
    assert.equal(next.llamado, false);
    assert.ok(res.cabeceras['Retry-After'], 'debería decir cuánto esperar');
});

test('cada IP lleva su propia cuenta', () => {
    const limitador = crearLimitador({ maximo: 1, ventanaMs: 1000 });

    limitador(req('3.3.3.3'), crearRes(), crearNext());

    const res = crearRes();
    const next = crearNext();
    limitador(req('4.4.4.4'), res, next);

    assert.equal(next.llamado, true, 'una IP no debería consumir el cupo de otra');
});

test('la ventana se reinicia cuando pasa el tiempo', () => {
    // El reloj se inyecta en lugar de esperar de verdad: un test que duerme
    // un segundo es un test que nadie corre.
    let ahora = 0;
    const limitador = crearLimitador({ maximo: 1, ventanaMs: 1000, reloj: () => ahora });

    limitador(req('5.5.5.5'), crearRes(), crearNext());

    const bloqueado = crearRes();
    limitador(req('5.5.5.5'), bloqueado, crearNext());
    assert.equal(bloqueado.codigo, 429);

    ahora = 1001;

    const res = crearRes();
    const next = crearNext();
    limitador(req('5.5.5.5'), res, next);
    assert.equal(next.llamado, true, 'pasada la ventana debería volver a dejar pasar');
});

test('no acumula IPs para siempre: limpia las vencidas', () => {
    let ahora = 0;
    const limitador = crearLimitador({ maximo: 5, ventanaMs: 1000, reloj: () => ahora });

    for (let i = 0; i < 50; i++) {
        limitador(req(`10.0.0.${i}`), crearRes(), crearNext());
    }
    assert.equal(limitador.tamano(), 50);

    ahora = 5000;
    limitador(req('10.0.1.1'), crearRes(), crearNext());

    assert.ok(
        limitador.tamano() < 50,
        `las entradas vencidas deberían haberse limpiado, quedaron ${limitador.tamano()}`
    );
});
