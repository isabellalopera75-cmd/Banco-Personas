/**
 * Lógica de merge de ediciones concurrentes.
 *
 * Está acá, sin base de datos ni Express, porque es la regla de negocio más
 * delicada del sistema: decide cuándo dos ediciones simultáneas conviven y
 * cuándo hay que frenar y pedirle a una persona que resuelva. Aislarla es lo
 * que permite probarla a fondo sin levantar nada.
 *
 * El sistema anterior resolvía esto en el cliente, reescribiendo la versión y
 * reintentando: el cambio del otro se pisaba en silencio.
 */

// Lo único que un cliente puede modificar. `id`, `version`, `cambio_seq`,
// `creado_por` y `deleted_at` quedan afuera a propósito: son control, no datos.
// Que la lista sea explícita significa que agregar una columna a la tabla no
// la vuelve editable por accidente.
const CAMPOS_EDITABLES = [
    'tipo_documento',
    'numero_documento',
    'primer_nombre',
    'segundo_nombre',
    'primer_apellido',
    'segundo_apellido',
    'fecha_nacimiento',
    'sexo',
    'telefono',
];

/**
 * Un campo opcional vacío significa "sin dato", no "el texto vacío". Si no se
 * unifican, borrar un teléfono queda como un cambio de null a '' y después de
 * '' a null: ruido permanente en la auditoría y conflictos inventados.
 */
const normalizarValor = (valor) => {
    if (valor === undefined || valor === null) return null;
    const texto = String(valor).trim();
    return texto === '' ? null : texto;
};

/** Las fechas llegan como Date desde Postgres y como texto desde el cliente. */
const normalizarParaComparar = (campo, valor) => {
    const normalizado = normalizarValor(valor);
    if (normalizado === null) return null;

    if (campo === 'fecha_nacimiento') {
        const fecha = valor instanceof Date ? valor : new Date(normalizado);
        return Number.isNaN(fecha.getTime()) ? normalizado : fecha.toISOString().slice(0, 10);
    }

    return normalizado;
};

/**
 * Filtra el cuerpo de la request a lo que de verdad se puede cambiar.
 *
 * Devuelve también lo rechazado: callar que se ignoró un campo hace que un
 * cliente mal escrito parezca funcionar hasta que alguien nota que ese dato
 * nunca se guardó.
 */
const normalizarCambios = (cuerpo = {}) => {
    const cambios = {};
    const rechazados = [];

    for (const [clave, valor] of Object.entries(cuerpo)) {
        if (!CAMPOS_EDITABLES.includes(clave)) {
            rechazados.push(clave);
            continue;
        }

        const normalizado = normalizarValor(valor);
        cambios[clave] =
            clave === 'tipo_documento' && normalizado ? normalizado.toUpperCase() : normalizado;
    }

    return { cambios, rechazados };
};

/**
 * De los campos enviados, cuáles cambian algo de verdad.
 *
 * El formulario manda el registro entero aunque se haya tocado un solo campo.
 * Sin este filtro, cada edición ensucia la auditoría con "cambió Rosa por
 * Rosa" y dispara conflictos donde nadie modificó nada.
 */
const camposRealmenteModificados = (personaActual, cambios) =>
    Object.keys(cambios).filter((campo) => {
        const antes = normalizarParaComparar(campo, personaActual[campo]);
        const despues = normalizarParaComparar(campo, cambios[campo]);
        return antes !== despues;
    });

/**
 * Decide qué hacer con una edición que llega desde un dispositivo.
 *
 *   SIN_CAMBIOS      el cambio no modifica nada. Se responde el estado actual.
 *   VERSION_INVALIDA el cliente dice venir de una versión que nunca existió.
 *   DIRECTO          nadie tocó el registro desde entonces.
 *   MERGE            hubo ediciones, pero sobre otros campos. Conviven.
 *   CONFLICTO        alguien tocó alguno de estos mismos campos.
 *
 * Un solo campo pisado alcanza para bloquear todo el cambio. No se aplica "la
 * parte que se puede": mergear a medias deja un registro que nunca existió, ni
 * el que había ni el que se quiso guardar.
 */
const decidirMerge = ({
    versionBase,
    versionActual,
    camposDelCambio,
    camposTocadosDesdeBase,
}) => {
    if (camposDelCambio.length === 0) {
        return { tipo: 'SIN_CAMBIOS' };
    }

    if (versionBase > versionActual) {
        return { tipo: 'VERSION_INVALIDA' };
    }

    if (versionBase === versionActual) {
        return { tipo: 'DIRECTO' };
    }

    const pisados = new Set(camposTocadosDesdeBase);
    const camposEnConflicto = camposDelCambio.filter((campo) => pisados.has(campo));

    if (camposEnConflicto.length > 0) {
        return { tipo: 'CONFLICTO', camposEnConflicto };
    }

    return { tipo: 'MERGE' };
};

module.exports = {
    CAMPOS_EDITABLES,
    normalizarCambios,
    camposRealmenteModificados,
    decidirMerge,
    normalizarValor,
    normalizarParaComparar,
};
