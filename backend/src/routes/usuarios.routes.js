const express = require('express');
const router = express.Router();

const { verificarToken, requerirAdmin } = require('../middleware/auth.middleware');
const {
    crearRegistrador,
    listarUsuarios,
    actualizarUsuario,
} = require('../controllers/usuarios.controller');

// La gestión de cuentas es del admin y solo del admin, siempre con conexión.
// Se aplica a todo el router en lugar de ruta por ruta: así, agregar un
// endpoint nuevo acá no puede quedar sin proteger por olvido.
router.use(verificarToken, requerirAdmin);

router.post('/', crearRegistrador);
router.get('/', listarUsuarios);
router.patch('/:id', actualizarUsuario);

module.exports = router;
