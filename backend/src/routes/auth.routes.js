const express = require('express');
const router = express.Router();

const { loginOperador, loginPersona } = require('../controllers/auth.controller');
const {
    limitarLoginOperador,
    limitarLoginPersona,
} = require('../middleware/limitarIntentos.middleware');

// Ambas rutas son públicas: se usan antes de tener un token.
//
// El alta de personas YA NO vive acá. En el sistema anterior /register era
// pública y cualquiera se registraba solo; ahora a las personas las da de alta
// un registrador autenticado, en /api/personas.

// Entrada de admin y registrador. El límite es holgado: acá hay contraseña,
// y un registrador en el campo se puede equivocar sin quedar bloqueado.
router.post('/login-operador', limitarLoginOperador, loginOperador);

// Entrada del rol usuario. El limitador acá no es una comodidad: esta ruta no
// pide contraseña, así que es la única barrera contra un barrido de documentos.
router.post('/login-persona', limitarLoginPersona, loginPersona);

module.exports = router;
