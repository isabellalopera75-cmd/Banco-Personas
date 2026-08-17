const express = require('express');
const router = express.Router();
const {
    obtenerPersonas,
    crearPersona,
    editarPersona,
    eliminarPersona,
    obtenerCambios,
    obtenerHistorial,
    obtenerGanadorSemana
} = require('../controllers/personas.controller');

router.get('/sync', obtenerCambios);
router.get('/ganador-semana', obtenerGanadorSemana);
router.get('/:id/historial', obtenerHistorial);
router.get('/', obtenerPersonas);
router.post('/', crearPersona);
router.put('/:id', editarPersona);
router.delete('/:id', eliminarPersona);

module.exports = router;