const express = require('express');
const router = express.Router();
const {
    verificarToken,
    requerirAdmin,
    requerirPropietarioOAdmin,
} = require('../middleware/auth.middleware');
const {
    obtenerPersonas,
    editarPersona,
    eliminarPersona,
    obtenerCambios,
    obtenerHistorial,
    obtenerGanadorSemana,
} = require('../controllers/personas.controller');

// Ninguna ruta de este router es pública: todas exigen un token válido.
// El alta de participantes vive en /api/auth/register, que sí es pública.
router.use(verificarToken);

// Disponibles para cualquier sesión autenticada.
router.get('/sync', obtenerCambios);
router.get('/ganador-semana', obtenerGanadorSemana);

// Solo el propio participante o el administrador.
router.get('/:id/historial', requerirPropietarioOAdmin, obtenerHistorial);
router.put('/:id', requerirPropietarioOAdmin, editarPersona);

// Solo el administrador.
router.get('/', requerirAdmin, obtenerPersonas);
router.delete('/:id', requerirAdmin, eliminarPersona);

module.exports = router;
