const test = require('node:test');
const assert = require('node:assert/strict');

const {
    CAMPOS_EDITABLES,
    normalizarCambios,
    camposRealmenteModificados,
    decidirMerge,
} = require('./mergePersonas');

// ===========================================================================
// normalizarCambios: qué del cuerpo de la request se toma en serio
// ===========================================================================

test('normalizarCambios descarta campos que no son editables', () => {
    const { cambios, rechazados } = normalizarCambios({
        telefono: '3001112233',
        version: 99,
        creado_por: 'otro-registrador',
        id: 'otro-id',
    });

    assert.deepEqual(Object.keys(cambios), ['telefono']);
    assert.deepEqual(rechazados.sort(), ['creado_por', 'id', 'version']);
});

test('normalizarCambios recorta espacios', () => {
    const { cambios } = normalizarCambios({ primer_nombre: '  Rosa  ' });
    assert.equal(cambios.primer_nombre, 'Rosa');
});

test('normalizarCambios convierte cadena vacía en null', () => {
    // Un campo opcional que el formulario deja vacío significa "sin dato",
    // no "el texto vacío". Si no se unifica, borrar un teléfono queda como un
    // cambio de null a '' y después a null: ruido eterno en la auditoría.
    const { cambios } = normalizarCambios({ segundo_nombre: '   ' });
    assert.equal(cambios.segundo_nombre, null);
});

test('normalizarCambios pone el tipo de documento en mayúsculas', () => {
    const { cambios } = normalizarCambios({ tipo_documento: 'cc' });
    assert.equal(cambios.tipo_documento, 'CC');
});

test('todos los campos editables son conocidos y no incluyen los de control', () => {
    for (const prohibido of ['id', 'version', 'cambio_seq', 'creado_por', 'deleted_at']) {
        assert.ok(
            !CAMPOS_EDITABLES.includes(prohibido),
            `${prohibido} no debería poder editarse desde el cliente`
        );
    }
});

// ===========================================================================
// camposRealmenteModificados: un cambio que no cambia nada no es un cambio
// ===========================================================================

const PERSONA = {
    tipo_documento: 'CC',
    numero_documento: '1234',
    primer_nombre: 'Rosa',
    segundo_nombre: null,
    primer_apellido: 'Perez',
    segundo_apellido: null,
    fecha_nacimiento: new Date('1960-05-01T00:00:00Z'),
    sexo: 'F',
    telefono: null,
};

test('detecta un campo que sí cambió', () => {
    const campos = camposRealmenteModificados(PERSONA, { telefono: '3001112233' });
    assert.deepEqual(campos, ['telefono']);
});

test('ignora un campo enviado con el mismo valor', () => {
    // El formulario manda el registro entero aunque el usuario haya tocado un
    // solo campo. Si no se filtra, cada edición ensucia la auditoría con
    // "cambió Rosa por Rosa" y, peor, dispara conflictos que no existen.
    const campos = camposRealmenteModificados(PERSONA, {
        primer_nombre: 'Rosa',
        telefono: '3001112233',
    });
    assert.deepEqual(campos, ['telefono']);
});

test('null y cadena vacía se consideran el mismo valor', () => {
    const campos = camposRealmenteModificados(PERSONA, { segundo_nombre: null });
    assert.deepEqual(campos, []);
});

test('compara fechas por su valor, no por su tipo', () => {
    // La base devuelve un Date y el cliente manda un texto. Comparados en
    // crudo nunca son iguales, y toda edición parecería cambiar la fecha.
    const campos = camposRealmenteModificados(PERSONA, { fecha_nacimiento: '1960-05-01' });
    assert.deepEqual(campos, []);
});

test('detecta un cambio real de fecha', () => {
    const campos = camposRealmenteModificados(PERSONA, { fecha_nacimiento: '1961-05-01' });
    assert.deepEqual(campos, ['fecha_nacimiento']);
});

test('un cambio vacío no modifica nada', () => {
    assert.deepEqual(camposRealmenteModificados(PERSONA, {}), []);
});

// ===========================================================================
// decidirMerge: el corazón del sistema
// ===========================================================================

test('sin ediciones intermedias se aplica directo', () => {
    const decision = decidirMerge({
        versionBase: 3,
        versionActual: 3,
        camposDelCambio: ['telefono'],
        camposTocadosDesdeBase: [],
    });
    assert.equal(decision.tipo, 'DIRECTO');
});

test('otro editó un campo distinto: se mergea', () => {
    // Es el caso que justifica todo el diseño. Un registrador agregó el
    // teléfono mientras el admin corregía el apellido. Los dos entran.
    const decision = decidirMerge({
        versionBase: 3,
        versionActual: 5,
        camposDelCambio: ['telefono'],
        camposTocadosDesdeBase: ['primer_apellido', 'segundo_apellido'],
    });
    assert.equal(decision.tipo, 'MERGE');
});

test('otro editó el MISMO campo: es conflicto', () => {
    const decision = decidirMerge({
        versionBase: 3,
        versionActual: 5,
        camposDelCambio: ['telefono'],
        camposTocadosDesdeBase: ['telefono'],
    });
    assert.equal(decision.tipo, 'CONFLICTO');
    assert.deepEqual(decision.camposEnConflicto, ['telefono']);
});

test('conflicto parcial: alcanza un solo campo pisado', () => {
    // No se mergea "la parte que se puede". Aplicar la mitad de una edición
    // deja un registro que nunca existió: ni el que había ni el que se quiso.
    const decision = decidirMerge({
        versionBase: 3,
        versionActual: 5,
        camposDelCambio: ['telefono', 'primer_nombre'],
        camposTocadosDesdeBase: ['primer_nombre'],
    });
    assert.equal(decision.tipo, 'CONFLICTO');
    assert.deepEqual(decision.camposEnConflicto, ['primer_nombre']);
});

test('varios campos en conflicto se informan todos', () => {
    const decision = decidirMerge({
        versionBase: 1,
        versionActual: 9,
        camposDelCambio: ['telefono', 'primer_nombre', 'sexo'],
        camposTocadosDesdeBase: ['sexo', 'telefono', 'fecha_nacimiento'],
    });
    assert.equal(decision.tipo, 'CONFLICTO');
    assert.deepEqual(decision.camposEnConflicto.sort(), ['sexo', 'telefono']);
});

test('una versión base mayor que la actual es incoherente', () => {
    // El cliente dice venir de una versión que el servidor nunca emitió.
    // Mergear eso sería inventar historia.
    const decision = decidirMerge({
        versionBase: 7,
        versionActual: 5,
        camposDelCambio: ['telefono'],
        camposTocadosDesdeBase: [],
    });
    assert.equal(decision.tipo, 'VERSION_INVALIDA');
});

test('un cambio sin campos no es un cambio', () => {
    const decision = decidirMerge({
        versionBase: 3,
        versionActual: 3,
        camposDelCambio: [],
        camposTocadosDesdeBase: [],
    });
    assert.equal(decision.tipo, 'SIN_CAMBIOS');
});

test('sin campos y con ediciones intermedias tampoco hay conflicto', () => {
    // Reenviar algo que ya no cambia nada no debería molestar a nadie. Es lo
    // que hace la outbox cuando reintenta una operación ya aplicada.
    const decision = decidirMerge({
        versionBase: 3,
        versionActual: 8,
        camposDelCambio: [],
        camposTocadosDesdeBase: ['telefono'],
    });
    assert.equal(decision.tipo, 'SIN_CAMBIOS');
});
