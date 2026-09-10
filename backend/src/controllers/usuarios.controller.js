const bcrypt = require('bcryptjs');
const { randomUUID } = require('crypto');
const pool = require('../db/pool');

const COSTO_BCRYPT = 12;
const LARGO_MINIMO_PASSWORD = 12;
const LARGO_MINIMO_USUARIO = 3;

// El hash nunca sale del servidor, ni siquiera hacia el admin.
const CAMPOS_PUBLICOS = 'id, usuario, rol, activo, creado_por, creado_en';

/**
 * POST /api/usuarios — el admin crea un registrador.
 *
 * Solo se crean registradores desde acá. Un segundo admin no se crea por API:
 * eso multiplicaría quién puede resolver conflictos y desactivar cuentas sin
 * dejar un rastro claro de quién habilitó a quién. El admin nace del script
 * de arranque, y punto.
 */
const crearRegistrador = async (req, res) => {
    const { usuario, password } = req.body || {};

    const nombreUsuario = String(usuario || '').trim();
    const clave = String(password || '');

    if (nombreUsuario.length < LARGO_MINIMO_USUARIO) {
        return res.status(400).json({
            error: `El usuario debe tener al menos ${LARGO_MINIMO_USUARIO} caracteres`,
        });
    }
    if (clave.length < LARGO_MINIMO_PASSWORD) {
        return res.status(400).json({
            error: `La contraseña debe tener al menos ${LARGO_MINIMO_PASSWORD} caracteres`,
        });
    }

    try {
        const passwordHash = await bcrypt.hash(clave, COSTO_BCRYPT);

        const resultado = await pool.query(
            `INSERT INTO usuarios (id, usuario, password_hash, rol, activo, creado_por)
             VALUES ($1, $2, $3, 'registrador', true, $4)
             RETURNING ${CAMPOS_PUBLICOS}`,
            [randomUUID(), nombreUsuario, passwordHash, req.usuario.sub]
        );

        return res.status(201).json(resultado.rows[0]);
    } catch (error) {
        // 23505: el índice único sobre lower(usuario).
        if (error.code === '23505') {
            return res.status(409).json({ error: 'Ese nombre de usuario ya está tomado' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al crear el registrador' });
    }
};

/**
 * GET /api/usuarios — listado para el admin.
 *
 * Incluye a los desactivados: el admin necesita verlos para reactivarlos, y
 * esconderlos haría parecer que el nombre de usuario está libre cuando no lo
 * está.
 */
const listarUsuarios = async (req, res) => {
    try {
        const resultado = await pool.query(
            `SELECT ${CAMPOS_PUBLICOS} FROM usuarios ORDER BY rol, lower(usuario)`
        );
        return res.json(resultado.rows);
    } catch (error) {
        console.error(error);
        return res.status(500).json({ error: 'Error al obtener los usuarios' });
    }
};

/**
 * PATCH /api/usuarios/:id — activar, desactivar o cambiar la contraseña.
 *
 * No se borran cuentas. Un registrador desactivado deja de entrar, pero sus
 * `creado_por` en personas y en el historial siguen apuntando a una fila que
 * existe. Borrarlo rompería la auditoría, que es justamente lo que una EPS
 * necesita conservar.
 */
const actualizarUsuario = async (req, res) => {
    const { id } = req.params;
    const { activo, password } = req.body || {};

    if (activo === undefined && password === undefined) {
        return res.status(400).json({ error: 'No se indicó ningún cambio' });
    }

    const asignaciones = [];
    const valores = [];

    if (activo !== undefined) {
        if (typeof activo !== 'boolean') {
            return res.status(400).json({ error: 'El campo activo debe ser true o false' });
        }
        valores.push(activo);
        asignaciones.push(`activo = $${valores.length}`);
    }

    if (password !== undefined) {
        const clave = String(password);
        if (clave.length < LARGO_MINIMO_PASSWORD) {
            return res.status(400).json({
                error: `La contraseña debe tener al menos ${LARGO_MINIMO_PASSWORD} caracteres`,
            });
        }
        valores.push(await bcrypt.hash(clave, COSTO_BCRYPT));
        asignaciones.push(`password_hash = $${valores.length}`);
    }

    valores.push(id);

    // Todo en una transacción. La regla "no dejar el sistema sin admin activo"
    // solo se puede comprobar DESPUÉS de aplicar el cambio, y hacerlo con dos
    // consultas sueltas — actualizar y después revertir si quedó mal — deja la
    // base inconsistente si el proceso muere entre una y otra. Acá, o entra
    // todo o no entra nada.
    const cliente = await pool.connect();

    try {
        await cliente.query('BEGIN');

        const resultado = await cliente.query(
            `UPDATE usuarios SET ${asignaciones.join(', ')}
              WHERE id = $${valores.length}
              RETURNING ${CAMPOS_PUBLICOS}`,
            valores
        );

        if (resultado.rows.length === 0) {
            await cliente.query('ROLLBACK');
            return res.status(404).json({ error: 'Usuario no encontrado' });
        }

        if (activo === false) {
            const admins = await cliente.query(
                "SELECT count(*)::int AS cantidad FROM usuarios WHERE rol = 'admin' AND activo"
            );

            if (admins.rows[0].cantidad === 0) {
                await cliente.query('ROLLBACK');
                return res.status(409).json({
                    error: 'No se puede desactivar al único administrador activo',
                });
            }
        }

        await cliente.query('COMMIT');
        return res.json(resultado.rows[0]);
    } catch (error) {
        await cliente.query('ROLLBACK').catch(() => {});

        if (error.code === '22P02') {
            return res.status(404).json({ error: 'Usuario no encontrado' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al actualizar el usuario' });
    } finally {
        cliente.release();
    }
};

module.exports = {
    crearRegistrador,
    listarUsuarios,
    actualizarUsuario,
};
