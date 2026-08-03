const express = require('express');
const router = express.Router();
const {
    obtenerPersonas,
    crearPersona,
    editarPersona,
    eliminarPersona,
    obtenerCambios
} = require('../controllers/personas.controller');

router.get('/sync', obtenerCambios);
router.get('/', obtenerPersonas);
router.post('/', crearPersona);
router.put('/:id', editarPersona);
router.delete('/:id', eliminarPersona);

module.exports = router;