const express = require('express');
const router = express.Router();
const { login, register } = require('../controllers/auth.controller');

// Ambas rutas son públicas: se usan antes de tener un token.
router.post('/register', register);
router.post('/login', login);

module.exports = router;