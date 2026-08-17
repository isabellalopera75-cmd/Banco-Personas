require('dotenv').config();
const express = require('express');
const cors = require('cors');
const personasRoutes = require('./routes/personas.routes');
const authRoutes = require('./routes/auth.routes');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

app.use('/api/personas', personasRoutes);
app.use('/api/auth', authRoutes);

// Endpoint de salud — para verificar que el servidor está vivo
app.get('/health', (req, res) => {
    res.json({ status: 'ok', mensaje: 'Servidor bancopersonas funcionando' });
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Servidor corriendo en http://0.0.0.0:${PORT}`);
});