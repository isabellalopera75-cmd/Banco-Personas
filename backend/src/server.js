const config = require('./config');
const express = require('express');
const cors = require('cors');
const pool = require('./db/pool');
const authRoutes = require('./routes/auth.routes');
const usuariosRoutes = require('./routes/usuarios.routes');
const personasRoutes = require('./routes/personas.routes');
const conflictosRoutes = require('./routes/conflictos.routes');

const app = express();

// La API corre detrás de un proxy (nginx / Dokploy). Sin esto, req.ip es
// siempre la IP del proxy y el limitador de intentos le contaría los intentos
// de todo el mundo a una sola dirección: el primero en equivocarse cinco veces
// dejaría a los demás afuera.
app.set('trust proxy', 1);

app.use(cors());
app.use(express.json());

app.use('/api/auth', authRoutes);
app.use('/api/usuarios', usuariosRoutes);
app.use('/api/personas', personasRoutes);
app.use('/api/conflictos', conflictosRoutes);

// Endpoint de salud.
//
// Consulta la base de datos a propósito. Un health check que solo responde
// "ok" porque el proceso está vivo es peor que no tener ninguno: informa que
// todo funciona mientras cada request de la aplicación devuelve 500.
app.get('/health', async (req, res) => {
    try {
        await pool.query('SELECT 1');
        res.json({ status: 'ok', base_de_datos: 'conectada' });
    } catch (error) {
        console.error('Health check falló:', error.message);
        res.status(503).json({ status: 'error', base_de_datos: 'sin conexión' });
    }
});

app.listen(config.port, '0.0.0.0', () => {
    console.log(`Servidor corriendo en http://0.0.0.0:${config.port}`);
});
