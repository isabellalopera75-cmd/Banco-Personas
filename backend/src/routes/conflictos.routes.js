const express = require('express');
const router = express.Router();

const {
    verificarToken,
    requerirAdmin,
    requerirOperador,
} = require('../middleware/auth.middleware');

const {
    listarConflictos,
    misConflictos,
    fusionarConflicto,
    crearComoNueva,
    descartarConflicto,
} = require('../controllers/conflictos.controller');

router.use(verificarToken);

// El registrador solo puede ver qué pasó con lo que él mandó. Va antes que
// cualquier ruta con parámetro para que "mios" no se lea como un id.
router.get('/mios', requerirOperador, misConflictos);

// Resolver conflictos es del admin: es quien decide si dos registros son la
// misma persona, y esa decisión afecta datos que otro cargó.
router.get('/', requerirAdmin, listarConflictos);
router.post('/:id/fusionar', requerirAdmin, fusionarConflicto);
router.post('/:id/crear-como-nueva', requerirAdmin, crearComoNueva);
router.post('/:id/descartar', requerirAdmin, descartarConflicto);

module.exports = router;
