// Estas variables se definen ANTES de requerir nada: config.js valida el
// entorno al cargarse y dotenv no pisa lo que ya está en process.env. Así los
// tests no dependen de que exista un .env con valores útiles.
process.env.DB_USER = process.env.DB_USER || 'test';
process.env.DB_PASSWORD = process.env.DB_PASSWORD || 'test';
process.env.DB_HOST = process.env.DB_HOST || 'localhost';
process.env.DB_PORT = process.env.DB_PORT || '5432';
process.env.DB_NAME = process.env.DB_NAME || 'test';
process.env.JWT_SECRET = 'secreto-solo-para-tests';

const test = require('node:test');
const assert = require('node:assert/strict');
const jwt = require('jsonwebtoken');

const {
    verificarToken,
    requerirRol,
    crearRequerirPropiedadSobrePersona,
} = require('./auth.middleware');

const SECRETO = process.env.JWT_SECRET;

const ID_ADMIN = '11111111-1111-4111-8111-111111111111';
const ID_REG_1 = '22222222-2222-4222-8222-222222222222';
const ID_REG_2 = '33333333-3333-4333-8333-333333333333';
const ID_PERSONA = '44444444-4444-4444-8444-444444444444';

/** Doble de `res` que recuerda con qué se lo llamó, sin depender de Express. */
function crearRes() {
    return {
        codigo: null,
        cuerpo: null,
        status(codigo) {
            this.codigo = codigo;
            return this;
        },
        json(cuerpo) {
            this.cuerpo = cuerpo;
            return this;
        },
    };
}

function crearReq({ token, usuario, params } = {}) {
    return {
        headers: token ? { authorization: `Bearer ${token}` } : {},
        usuario,
        params: params || {},
    };
}

/** Devuelve una función next() que registra si fue llamada. */
function crearNext() {
    const next = () => {
        next.llamado = true;
    };
    next.llamado = false;
    return next;
}

// ===========================================================================
test('verificarToken rechaza si no hay header Authorization', () => {
    const res = crearRes();
    const next = crearNext();

    verificarToken(crearReq(), res, next);

    assert.equal(res.codigo, 401);
    assert.equal(next.llamado, false);
});

test('verificarToken rechaza un esquema que no sea Bearer', () => {
    const res = crearRes();
    const next = crearNext();
    const req = { headers: { authorization: 'Basic loquesea' }, params: {} };

    verificarToken(req, res, next);

    assert.equal(res.codigo, 401);
    assert.equal(next.llamado, false);
});

test('verificarToken rechaza un token firmado con otro secreto', () => {
    const token = jwt.sign({ sub: ID_ADMIN, rol: 'admin' }, 'otro-secreto');
    const res = crearRes();
    const next = crearNext();

    verificarToken(crearReq({ token }), res, next);

    assert.equal(res.codigo, 401);
    assert.equal(next.llamado, false);
});

test('verificarToken rechaza un token vencido', () => {
    const token = jwt.sign({ sub: ID_ADMIN, rol: 'admin' }, SECRETO, { expiresIn: '-1s' });
    const res = crearRes();
    const next = crearNext();

    verificarToken(crearReq({ token }), res, next);

    assert.equal(res.codigo, 401);
    assert.equal(next.llamado, false);
});

test('verificarToken acepta un token válido y expone el payload', () => {
    const token = jwt.sign({ sub: ID_REG_1, rol: 'registrador' }, SECRETO);
    const req = crearReq({ token });
    const res = crearRes();
    const next = crearNext();

    verificarToken(req, res, next);

    assert.equal(next.llamado, true);
    assert.equal(req.usuario.sub, ID_REG_1);
    assert.equal(req.usuario.rol, 'registrador');
});

// ===========================================================================
test('requerirRol deja pasar al rol esperado', () => {
    const res = crearRes();
    const next = crearNext();

    requerirRol('admin')(crearReq({ usuario: { sub: ID_ADMIN, rol: 'admin' } }), res, next);

    assert.equal(next.llamado, true);
    assert.equal(res.codigo, null);
});

test('requerirRol acepta cualquiera de varios roles', () => {
    const middleware = requerirRol('admin', 'registrador');
    const res = crearRes();
    const next = crearNext();

    middleware(crearReq({ usuario: { sub: ID_REG_1, rol: 'registrador' } }), res, next);

    assert.equal(next.llamado, true);
});

test('requerirRol rechaza con 403 a un rol que no está en la lista', () => {
    const res = crearRes();
    const next = crearNext();

    requerirRol('admin')(crearReq({ usuario: { sub: ID_REG_1, rol: 'registrador' } }), res, next);

    assert.equal(res.codigo, 403);
    assert.equal(next.llamado, false);
});

test('requerirRol rechaza si no hay usuario en la request', () => {
    const res = crearRes();
    const next = crearNext();

    requerirRol('admin')(crearReq(), res, next);

    assert.equal(res.codigo, 403);
    assert.equal(next.llamado, false);
});

// ===========================================================================
// El middleware de propiedad recibe su consulta por parámetro. No es adorno:
// permite probar la regla de autorización sin levantar una base de datos, que
// es donde de verdad se cometen los errores de permisos.
// ===========================================================================
test('propiedad: el admin pasa sin que se consulte la base', async () => {
    let seConsulto = false;
    const middleware = crearRequerirPropiedadSobrePersona(async () => {
        seConsulto = true;
        return null;
    });

    const res = crearRes();
    const next = crearNext();

    await middleware(
        crearReq({ usuario: { sub: ID_ADMIN, rol: 'admin' }, params: { id: ID_PERSONA } }),
        res,
        next
    );

    assert.equal(next.llamado, true);
    assert.equal(seConsulto, false, 'el admin no debería costar una consulta');
});

test('propiedad: el registrador pasa sobre una persona que registró él', async () => {
    const middleware = crearRequerirPropiedadSobrePersona(async () => ID_REG_1);
    const res = crearRes();
    const next = crearNext();

    await middleware(
        crearReq({ usuario: { sub: ID_REG_1, rol: 'registrador' }, params: { id: ID_PERSONA } }),
        res,
        next
    );

    assert.equal(next.llamado, true);
});

test('propiedad: el registrador NO pasa sobre una persona de otro registrador', async () => {
    const middleware = crearRequerirPropiedadSobrePersona(async () => ID_REG_2);
    const res = crearRes();
    const next = crearNext();

    await middleware(
        crearReq({ usuario: { sub: ID_REG_1, rol: 'registrador' }, params: { id: ID_PERSONA } }),
        res,
        next
    );

    assert.equal(res.codigo, 403);
    assert.equal(next.llamado, false);
});

test('propiedad: persona inexistente da 404, no 403', async () => {
    // Distinguirlos importa: un 403 sobre algo que no existe le confirma a
    // quien prueba ids al azar que ese id sí existe y es de otro.
    const middleware = crearRequerirPropiedadSobrePersona(async () => null);
    const res = crearRes();
    const next = crearNext();

    await middleware(
        crearReq({ usuario: { sub: ID_REG_1, rol: 'registrador' }, params: { id: ID_PERSONA } }),
        res,
        next
    );

    assert.equal(res.codigo, 404);
    assert.equal(next.llamado, false);
});

test('propiedad: el rol usuario nunca puede editar, ni su propio registro', async () => {
    // La persona es de solo lectura por decisión de negocio. Que el id del
    // token coincida con el de la ruta no la habilita a escribir.
    const middleware = crearRequerirPropiedadSobrePersona(async () => ID_REG_1);
    const res = crearRes();
    const next = crearNext();

    await middleware(
        crearReq({ usuario: { sub: ID_PERSONA, rol: 'usuario' }, params: { id: ID_PERSONA } }),
        res,
        next
    );

    assert.equal(res.codigo, 403);
    assert.equal(next.llamado, false);
});
