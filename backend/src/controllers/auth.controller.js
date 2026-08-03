const pool = require('../db/pool');

const login = async (req, res) => {
    const { documento, nombre, rol, password } = req.body;

    try {
        if (rol === 'admin') {
            // Validación fija para Admin
            if (nombre === 'admin' && password === 'admin123') {
                return res.json({
                    success: true,
                    rol: 'admin'
                });
            } else {
                return res.status(401).json({
                    success: false,
                    error: 'Credenciales de administrador incorrectas'
                });
            }
        } else if (rol === 'usuario') {
            // Validación contra la DB para Usuario
            const resultado = await pool.query(
                'SELECT * FROM personas WHERE documento = $1 AND nombre = $2 AND deleted_at IS NULL',
                [documento, nombre]
            );

            if (resultado.rows.length > 0) {
                return res.json({
                    success: true,
                    rol: 'usuario',
                    persona: resultado.rows[0]
                });
            } else {
                return res.status(401).json({
                    success: false,
                    error: 'Documento o nombre no coinciden con ningún registro'
                });
            }
        } else {
            return res.status(400).json({
                success: false,
                error: 'Rol no válido'
            });
        }
    } catch (error) {
        console.error(error);
        res.status(500).json({ success: false, error: 'Error en el servidor' });
    }
};

module.exports = {
    login
};