const { randomUUID } = require('crypto');
const pool = require('../db/pool');
const {
    CAMPOS_EDITABLES,
    normalizarCambios,
    camposRealmenteModificados,
} = require('../domain/mergePersonas');

const CAMPOS_PERSONA = `id, tipo_documento, numero_documento,
    primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,
    fecha_nacimiento, sexo, telefono,
    creado_por, version, cambio_seq, updated_at, deleted_at`;

const OBLIGATORIOS_ALTA = [
    'tipo_documento',
    'numero_documento',
    'primer_nombre',
    'primer_apellido',
    'fecha_nacimiento',
    'sexo',
];

const textoDe = (valor) => {
    if (valor === null || valor === undefined) return null;
    if (valor instanceof Date) return valor.toISOString().slice(0, 10);
    return String(valor);
};

/**
 * Toma el conflicto y lo deja bloqueado hasta el COMMIT.
 *
 * Devuelve un motivo en lugar de lanzar porque cada caso tiene su respuesta:
 * un conflicto que no existe no es lo mismo que uno que otro admin ya resolvió
 * mientras este lo miraba en pantalla.
 */
const tomarConflictoPendiente = async (cliente, id) => {
    const resultado = await cliente.query(
        'SELECT * FROM conflictos WHERE id = $1 FOR UPDATE',
        [id]
    );

    if (resultado.rows.length === 0) {
        return { error: { estado: 404, cuerpo: { error: 'Conflicto no encontrado' } } };
    }

    const conflicto = resultado.rows[0];

    if (conflicto.estado !== 'PENDIENTE') {
        return {
            error: {
                estado: 409,
                cuerpo: {
                    error: 'Ese conflicto ya fue resuelto',
                    codigo: 'YA_RESUELTO',
                    estado_actual: conflicto.estado,
                    resuelto_por: conflicto.resuelto_por,
                },
            },
        };
    }

    return { conflicto };
};

const cerrarConflicto = (cliente, { id, estado, adminId, nota }) =>
    cliente.query(
        `UPDATE conflictos
            SET estado = $2, resuelto_por = $3, resuelto_en = now(), nota_resolucion = $4
          WHERE id = $1`,
        [id, estado, adminId, nota || null]
    );

// ===========================================================================
// GET /api/conflictos — la cola del admin
// ===========================================================================
const listarConflictos = async (req, res) => {
    const estado = String(req.query.estado || 'PENDIENTE').toUpperCase();
    const limite = Math.min(Number(req.query.limite) || 50, 200);

    if (!['PENDIENTE', 'RESUELTO', 'DESCARTADO', 'TODOS'].includes(estado)) {
        return res.status(400).json({ error: 'Estado no válido' });
    }

    try {
        // Se trae la persona con la que chocó en la misma consulta: el admin
        // necesita ver las dos versiones enfrentadas para decidir, y pedirlas
        // de a una sería una consulta por fila de la cola.
        const resultado = await pool.query(
            `SELECT c.id, c.tipo, c.estado, c.datos_enviados, c.version_base,
                    c.persona_id_cliente, c.enviado_en, c.resuelto_en, c.nota_resolucion,
                    quien.usuario  AS enviado_por,
                    resolvio.usuario AS resuelto_por,
                    to_jsonb(p) - 'password_hash' AS persona_existente
               FROM conflictos c
               JOIN usuarios quien    ON quien.id = c.enviado_por
          LEFT JOIN usuarios resolvio ON resolvio.id = c.resuelto_por
               JOIN (SELECT ${CAMPOS_PERSONA} FROM personas) p ON p.id = c.persona_existente_id
              WHERE ($1 = 'TODOS' OR c.estado = $1)
              ORDER BY c.enviado_en
              LIMIT $2`,
            [estado, limite]
        );

        return res.json({ conflictos: resultado.rows });
    } catch (error) {
        console.error(error);
        return res.status(500).json({ error: 'Error al obtener los conflictos' });
    }
};

// ===========================================================================
// GET /api/conflictos/mios — qué pasó con lo que mandé
//
// El registrador necesita saberlo: en su teléfono ese registro quedó marcado
// EN_REVISION y hasta que el admin decida, no tiene otra forma de enterarse.
// ===========================================================================
const misConflictos = async (req, res) => {
    try {
        const resultado = await pool.query(
            `SELECT id, tipo, estado, persona_id_cliente, persona_existente_id,
                    datos_enviados, enviado_en, resuelto_en, nota_resolucion
               FROM conflictos
              WHERE enviado_por = $1
              ORDER BY enviado_en DESC
              LIMIT 200`,
            [req.usuario.sub]
        );
        return res.json({ conflictos: resultado.rows });
    } catch (error) {
        console.error(error);
        return res.status(500).json({ error: 'Error al obtener los conflictos' });
    }
};

// ===========================================================================
// POST /api/conflictos/:id/fusionar
//
// El admin elige campo por campo qué valor queda. Es la misma maquinaria que
// el merge automático: por eso el historial se guarda por campo.
// ===========================================================================
const fusionarConflicto = async (req, res) => {
    const { id } = req.params;
    const { campos, nota } = req.body || {};

    if (campos !== undefined && (typeof campos !== 'object' || Array.isArray(campos))) {
        return res.status(400).json({ error: 'campos debe ser un objeto' });
    }

    const cliente = await pool.connect();

    try {
        await cliente.query('BEGIN');

        const { conflicto, error } = await tomarConflictoPendiente(cliente, id);
        if (error) {
            await cliente.query('ROLLBACK');
            return res.status(error.estado).json(error.cuerpo);
        }

        const actual = await cliente.query(
            `SELECT ${CAMPOS_PERSONA} FROM personas WHERE id = $1 FOR UPDATE`,
            [conflicto.persona_existente_id]
        );

        const persona = actual.rows[0];

        if (!persona || persona.deleted_at) {
            await cliente.query('ROLLBACK');
            return res.status(409).json({
                error: 'La persona con la que chocaba fue dada de baja. Revisar como alta nueva.',
                codigo: 'PERSONA_DADA_DE_BAJA',
            });
        }

        const { cambios, rechazados } = normalizarCambios(campos || {});
        const camposDelCambio = camposRealmenteModificados(persona, cambios);

        // Cero campos es una resolución legítima: significa "lo que ya está es
        // correcto, el intento no aporta nada". Se cierra el conflicto igual,
        // porque dejarlo pendiente lo haría reaparecer en la cola para siempre.
        if (camposDelCambio.length > 0) {
            const nuevaVersion = persona.version + 1;
            const asignaciones = camposDelCambio.map((campo, i) => `${campo} = $${i + 2}`);
            const valores = camposDelCambio.map((campo) => cambios[campo]);

            await cliente.query(
                `UPDATE personas SET ${asignaciones.join(', ')}, version = $${valores.length + 2}
                  WHERE id = $1`,
                [persona.id, ...valores, nuevaVersion]
            );

            for (const campo of camposDelCambio) {
                await cliente.query(
                    `INSERT INTO historial_cambios
                        (persona_id, version, operacion, campo,
                         valor_anterior, valor_nuevo, realizado_por, conflicto_id)
                     VALUES ($1, $2, 'UPDATE', $3, $4, $5, $6, $7)`,
                    [
                        persona.id,
                        nuevaVersion,
                        campo,
                        textoDe(persona[campo]),
                        textoDe(cambios[campo]),
                        // Lo aplica el admin: es quien tomó la decisión. Quién
                        // había aportado el dato queda en el conflicto, que
                        // esta misma fila referencia.
                        req.usuario.sub,
                        conflicto.id,
                    ]
                );
            }
        }

        await cerrarConflicto(cliente, {
            id: conflicto.id,
            estado: 'RESUELTO',
            adminId: req.usuario.sub,
            nota,
        });

        const final = await cliente.query(
            `SELECT ${CAMPOS_PERSONA} FROM personas WHERE id = $1`,
            [persona.id]
        );

        await cliente.query('COMMIT');

        return res.json({
            persona: final.rows[0],
            campos_aplicados: camposDelCambio,
            rechazados,
        });
    } catch (error) {
        await cliente.query('ROLLBACK').catch(() => {});

        if (error.code === '23505') {
            return res.status(409).json({
                error: 'Ese documento ya pertenece a otra persona',
                codigo: 'DOCUMENTO_DUPLICADO',
            });
        }
        if (error.code === '23514' || error.code === '22007' || error.code === '22P02') {
            return res.status(400).json({ error: 'Hay campos con valores no válidos' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al fusionar el conflicto' });
    } finally {
        cliente.release();
    }
};

// ===========================================================================
// POST /api/conflictos/:id/crear-como-nueva
//
// "No son la misma persona: alguien tipeó mal un dígito." El intento entra
// como registro nuevo, con el documento corregido.
// ===========================================================================
const crearComoNueva = async (req, res) => {
    const { id } = req.params;
    const { tipo_documento, numero_documento, nota } = req.body || {};

    if (!tipo_documento || !numero_documento) {
        return res.status(400).json({
            error: 'Hay que indicar el tipo y número de documento corregidos',
        });
    }

    const cliente = await pool.connect();

    try {
        await cliente.query('BEGIN');

        const { conflicto, error } = await tomarConflictoPendiente(cliente, id);
        if (error) {
            await cliente.query('ROLLBACK');
            return res.status(error.estado).json(error.cuerpo);
        }

        if (conflicto.tipo !== 'ALTA_DUPLICADA') {
            await cliente.query('ROLLBACK');
            return res.status(400).json({
                error: 'Solo un alta duplicada puede convertirse en registro nuevo',
                codigo: 'TIPO_INCORRECTO',
            });
        }

        const { cambios } = normalizarCambios({
            ...conflicto.datos_enviados,
            tipo_documento,
            numero_documento,
        });

        const faltantes = OBLIGATORIOS_ALTA.filter((campo) => !cambios[campo]);
        if (faltantes.length > 0) {
            await cliente.query('ROLLBACK');
            return res.status(400).json({
                error: `Al intento le faltan campos obligatorios: ${faltantes.join(', ')}`,
                campos: faltantes,
            });
        }

        // Se reusa el UUID que había generado el dispositivo. Ese teléfono ya
        // tiene ese id en su base local: con cualquier otro, al sincronizar
        // vería el registro como ajeno y se quedaría con un duplicado propio.
        const nuevoId = conflicto.persona_id_cliente || randomUUID();

        const columnas = CAMPOS_EDITABLES.filter((campo) => cambios[campo] !== undefined);
        const valores = columnas.map((campo) => cambios[campo]);
        const marcadores = columnas.map((_, i) => `$${i + 3}`).join(', ');

        // creado_por es quien lo mandó, no el admin. Fue su trabajo de campo,
        // y además así el registro entra en SU sincronización y puede editarlo.
        const creada = await cliente.query(
            `INSERT INTO personas (id, creado_por, ${columnas.join(', ')})
             VALUES ($1, $2, ${marcadores})
             RETURNING ${CAMPOS_PERSONA}`,
            [nuevoId, conflicto.enviado_por, ...valores]
        );

        await cliente.query(
            `INSERT INTO historial_cambios
                (persona_id, version, operacion, realizado_por, conflicto_id)
             VALUES ($1, 1, 'CREATE', $2, $3)`,
            [nuevoId, conflicto.enviado_por, conflicto.id]
        );

        await cerrarConflicto(cliente, {
            id: conflicto.id,
            estado: 'RESUELTO',
            adminId: req.usuario.sub,
            nota: nota || 'Se creó como registro nuevo con el documento corregido',
        });

        await cliente.query('COMMIT');
        return res.status(201).json({ persona: creada.rows[0] });
    } catch (error) {
        await cliente.query('ROLLBACK').catch(() => {});

        if (error.code === '23505') {
            return res.status(409).json({
                error: 'Ese documento corregido también está tomado',
                codigo: 'DOCUMENTO_DUPLICADO',
            });
        }
        if (error.code === '23514' || error.code === '22007' || error.code === '22P02') {
            return res.status(400).json({ error: 'Hay campos con valores no válidos' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al crear el registro nuevo' });
    } finally {
        cliente.release();
    }
};

// ===========================================================================
// POST /api/conflictos/:id/descartar
// ===========================================================================
const descartarConflicto = async (req, res) => {
    const { id } = req.params;
    const nota = String((req.body && req.body.nota) || '').trim();

    // La nota es obligatoria. Descartar es tirar trabajo de campo de otra
    // persona: que quede escrito por qué no es burocracia, es lo que después
    // permite discutirlo.
    if (nota.length < 5) {
        return res
            .status(400)
            .json({ error: 'Hay que explicar por qué se descarta (mínimo 5 caracteres)' });
    }

    const cliente = await pool.connect();

    try {
        await cliente.query('BEGIN');

        const { conflicto, error } = await tomarConflictoPendiente(cliente, id);
        if (error) {
            await cliente.query('ROLLBACK');
            return res.status(error.estado).json(error.cuerpo);
        }

        await cerrarConflicto(cliente, {
            id: conflicto.id,
            estado: 'DESCARTADO',
            adminId: req.usuario.sub,
            nota,
        });

        await cliente.query('COMMIT');

        // El intento sigue guardado en `datos_enviados`. Descartado no es
        // borrado: si alguien reclama, el dato está.
        return res.json({ id: conflicto.id, estado: 'DESCARTADO' });
    } catch (error) {
        await cliente.query('ROLLBACK').catch(() => {});
        if (error.code === '22P02') {
            return res.status(404).json({ error: 'Conflicto no encontrado' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al descartar el conflicto' });
    } finally {
        cliente.release();
    }
};

module.exports = {
    listarConflictos,
    misConflictos,
    fusionarConflicto,
    crearComoNueva,
    descartarConflicto,
};
