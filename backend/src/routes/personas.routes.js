const express = require('express');
const router = express.Router();

const {
    verificarToken,
    requerirAdmin,
    requerirOperador,
    requerirPropiedadSobrePersona,
} = require('../middleware/auth.middleware');

const {
    crearPersona,
    editarPersona,
    eliminarPersona,
    obtenerCambios,
    listarPersonas,
    obtenerPersona,
    obtenerHistorial,
} = require('../controllers/personas.controller');

// Ninguna ruta de acá es pública. El alta del sistema anterior sí lo era, y
// esa era justamente la razón por la que no existía la figura del registrador:
// cada persona se anotaba sola.
router.use(verificarToken);

// --- Escritura: admin, o el registrador sobre lo que él registró ------------
router.post('/', requerirOperador, crearPersona);
router.put('/:id', requerirOperador, requerirPropiedadSobrePersona, editarPersona);
router.delete('/:id', requerirOperador, requerirPropiedadSobrePersona, eliminarPersona);

// --- Sincronización: el propio controlador filtra por rol -------------------
// El registrador baja solo lo suyo, el admin todo. La ruta va antes de
// '/:id' porque si no, Express tomaría "sync" como un id.
router.get('/sync', requerirOperador, obtenerCambios);

// --- Lectura ---------------------------------------------------------------
router.get('/', requerirAdmin, listarPersonas);
router.get('/:id/historial', requerirAdmin, obtenerHistorial);

// Admin, registrador propietario, o la propia persona sobre su registro. El
// control fino de esos tres casos vive en el controlador, porque el rol
// usuario no pasa por el middleware de propiedad: ese solo autoriza escritura.
router.get('/:id', obtenerPersona);

module.exports = router;
