const { randomUUID } = require('crypto');
const pool = require('../db/pool');
const {
    CAMPOS_EDITABLES,
    normalizarCambios,
    camposRealmenteModificados,
    decidirMerge,
} = require('../domain/mergePersonas');

const CAMPOS_PUBLICOS = `id, tipo_documento, numero_documento,
    primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,
    fecha_nacimiento, sexo, telefono,
    creado_por, version, cambio_seq, updated_at, deleted_at`;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const LIMITE_SYNC_POR_DEFECTO = 500;
const LIMITE_SYNC_MAXIMO = 1000;

const OBLIGATORIOS = [
    'tipo_documento',
    'numero_documento',
    'primer_nombre',
    'primer_apellido',
    'fecha_nacimiento',
    'sexo',
];

/**
 * Registra en la auditoría. Recibe el cliente de la transacción, no el pool:
 * el historial y el cambio que describe tienen que entrar o fallar juntos.
 * En el sistema anterior iban en consultas sueltas y dos ediciones a la vez
 * dejaban un historial que no correspondía con los datos.
 */
const registrarEnHistorial = async (cliente, { personaId, version, operacion, autor, campos }) => {
    if (operacion !== 'UPDATE') {
        await cliente.query(
            `INSERT INTO historial_cambios (persona_id, version, operacion, realizado_por)
             VALUES ($1, $2, $3, $4)`,
            [personaId, version, operacion, autor]
        );
        return;
    }

    for (const { campo, anterior, nuevo } of campos) {
        await cliente.query(
            `INSERT INTO historial_cambios
                (persona_id, version, operacion, campo, valor_anterior, valor_nuevo, realizado_por)
             VALUES ($1, $2, 'UPDATE', $3, $4, $5, $6)`,
            [personaId, version, campo, anterior, nuevo, autor]
        );
    }
};

const textoDe = (valor) => {
    if (valor === null || valor === undefined) return null;
    if (valor instanceof Date) return valor.toISOString().slice(0, 10);
    return String(valor);
};

// ===========================================================================
// POST /api/personas — alta
//
// El id lo genera el dispositivo para que el alta funcione sin conexión, así
// que la operación TIENE que ser idempotente: la outbox puede reintentar la
// misma alta varias veces y todas deben devolver lo mismo.
// ===========================================================================
const crearPersona = async (req, res) => {
    const { id } = req.body || {};

    if (!UUID.test(String(id || ''))) {
        return res.status(400).json({ error: 'El id debe ser un UUID válido' });
    }

    const { cambios, rechazados } = normalizarCambios(req.body);

    const faltantes = OBLIGATORIOS.filter((campo) => !cambios[campo]);
    if (faltantes.length > 0) {
        return res.status(400).json({
            error: `Faltan campos obligatorios: ${faltantes.join(', ')}`,
            campos: faltantes,
        });
    }

    const columnas = CAMPOS_EDITABLES.filter((campo) => cambios[campo] !== undefined);
    const valores = columnas.map((campo) => cambios[campo]);
    const marcadores = columnas.map((_, i) => `$${i + 3}`).join(', ');

    const cliente = await pool.connect();

    try {
        await cliente.query('BEGIN');

        // ON CONFLICT DO NOTHING sin nombrar la restricción cubre las dos que
        // pueden saltar: la clave primaria (reintento de la outbox) y el
        // documento repetido. Se distinguen después, consultando: así el alta
        // duplicada no llega como excepción y no aborta la transacción.
        const insercion = await cliente.query(
            `INSERT INTO personas (id, creado_por, ${columnas.join(', ')})
             VALUES ($1, $2, ${marcadores})
             ON CONFLICT DO NOTHING
             RETURNING ${CAMPOS_PUBLICOS}`,
            [id, req.usuario.sub, ...valores]
        );

        if (insercion.rows.length > 0) {
            const persona = insercion.rows[0];
            await registrarEnHistorial(cliente, {
                personaId: persona.id,
                version: persona.version,
                operacion: 'CREATE',
                autor: req.usuario.sub,
            });
            await cliente.query('COMMIT');
            return res.status(201).json({ persona, rechazados });
        }

        // No insertó. ¿Es el mismo id que ya está, o es otro documento igual?
        const mismoId = await cliente.query(
            `SELECT ${CAMPOS_PUBLICOS} FROM personas WHERE id = $1`,
            [id]
        );

        if (mismoId.rows.length > 0) {
            // Reintento de la outbox. Se responde lo mismo que la primera vez.
            await cliente.query('COMMIT');
            return res.status(200).json({ persona: mismoId.rows[0], reintento: true });
        }

        // Alta duplicada: otro registrador ya dio de alta este documento, con
        // otro id, probablemente sin conexión.
        //
        // Acá está el cambio de fondo respecto del sistema anterior. Antes se
        // respondía 409 y el teléfono marcaba el registro y lo borraba de la
        // cola: esos datos nunca salían del dispositivo y se perdían para
        // siempre. Ahora el intento queda guardado EN EL SERVIDOR antes de
        // responder, y recién por eso el teléfono puede descartarlo tranquilo.
        const existente = await cliente.query(
            `SELECT id FROM personas
              WHERE tipo_documento = $1 AND numero_documento = $2 AND deleted_at IS NULL`,
            [cambios.tipo_documento, cambios.numero_documento]
        );

        if (existente.rows.length === 0) {
            // Choca con una restricción que no es el documento activo. No hay
            // contra qué abrir un conflicto, así que se informa y no se pierde
            // nada en silencio.
            await cliente.query('ROLLBACK');
            return res.status(409).json({
                error: 'El registro no se pudo guardar por un conflicto de datos',
                codigo: 'CONFLICTO_DESCONOCIDO',
            });
        }

        const conflictoId = randomUUID();
        await cliente.query(
            `INSERT INTO conflictos
                (id, tipo, persona_existente_id, persona_id_cliente, datos_enviados, enviado_por)
             VALUES ($1, 'ALTA_DUPLICADA', $2, $3, $4, $5)`,
            [conflictoId, existente.rows[0].id, id, JSON.stringify(cambios), req.usuario.sub]
        );

        await cliente.query('COMMIT');

        return res.status(409).json({
            error: 'Ese documento ya fue registrado por otra persona. Quedó en revisión.',
            codigo: 'ALTA_DUPLICADA',
            conflicto_id: conflictoId,
            persona_existente_id: existente.rows[0].id,
        });
    } catch (error) {
        await cliente.query('ROLLBACK').catch(() => {});

        if (error.code === '23514' || error.code === '22007' || error.code === '22P02') {
            return res.status(400).json({ error: 'Hay campos con valores no válidos' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al registrar la persona' });
    } finally {
        cliente.release();
    }
};

// ===========================================================================
// PUT /api/personas/:id — edición con merge por campo
// ===========================================================================
const editarPersona = async (req, res) => {
    const { id } = req.params;
    const versionBase = Number(
        req.body && req.body.version_base !== undefined ? req.body.version_base : NaN
    );

    if (!Number.isInteger(versionBase) || versionBase < 1) {
        return res
            .status(400)
            .json({ error: 'Hay que indicar version_base: la versión sobre la que se editó' });
    }

    const { cambios, rechazados } = normalizarCambios(req.body.cambios || {});

    const cliente = await pool.connect();

    try {
        await cliente.query('BEGIN');

        // FOR UPDATE bloquea la fila hasta el COMMIT. Sin esto, dos ediciones
        // simultáneas leen el mismo estado, las dos deciden que no hay
        // conflicto y la segunda pisa a la primera.
        const actual = await cliente.query(
            `SELECT ${CAMPOS_PUBLICOS} FROM personas
              WHERE id = $1 AND deleted_at IS NULL
              FOR UPDATE`,
            [id]
        );

        if (actual.rows.length === 0) {
            await cliente.query('ROLLBACK');
            return res.status(404).json({ error: 'Persona no encontrada' });
        }

        const persona = actual.rows[0];
        const camposDelCambio = camposRealmenteModificados(persona, cambios);

        const tocados = await cliente.query(
            `SELECT DISTINCT campo FROM historial_cambios
              WHERE persona_id = $1 AND version > $2 AND campo IS NOT NULL`,
            [id, versionBase]
        );

        const decision = decidirMerge({
            versionBase,
            versionActual: persona.version,
            camposDelCambio,
            camposTocadosDesdeBase: tocados.rows.map((f) => f.campo),
        });

        if (decision.tipo === 'SIN_CAMBIOS') {
            await cliente.query('COMMIT');
            return res.status(200).json({ persona, sin_cambios: true });
        }

        if (decision.tipo === 'VERSION_INVALIDA') {
            await cliente.query('ROLLBACK');
            return res.status(409).json({
                error: 'La versión enviada no existe en el servidor',
                codigo: 'VERSION_INVALIDA',
                version_servidor: persona.version,
            });
        }

        if (decision.tipo === 'CONFLICTO') {
            const conflictoId = randomUUID();
            await cliente.query(
                `INSERT INTO conflictos
                    (id, tipo, persona_existente_id, datos_enviados, version_base, enviado_por)
                 VALUES ($1, 'EDICION_CONCURRENTE', $2, $3, $4, $5)`,
                [
                    conflictoId,
                    id,
                    JSON.stringify(cambios),
                    versionBase,
                    req.usuario.sub,
                ]
            );
            await cliente.query('COMMIT');

            return res.status(409).json({
                error: 'Alguien más editó estos mismos campos. Quedó en revisión.',
                codigo: 'EDICION_CONCURRENTE',
                conflicto_id: conflictoId,
                campos_en_conflicto: decision.camposEnConflicto,
                persona_servidor: persona,
            });
        }

        // DIRECTO o MERGE. Se escriben SOLO los campos que cambiaron: eso es
        // lo que permite que la edición de otro sobre otros campos sobreviva.
        const nuevaVersion = persona.version + 1;
        const asignaciones = camposDelCambio.map((campo, i) => `${campo} = $${i + 2}`);
        const valores = camposDelCambio.map((campo) => cambios[campo]);

        const actualizada = await cliente.query(
            `UPDATE personas SET ${asignaciones.join(', ')}, version = $${valores.length + 2}
              WHERE id = $1
              RETURNING ${CAMPOS_PUBLICOS}`,
            [id, ...valores, nuevaVersion]
        );

        await registrarEnHistorial(cliente, {
            personaId: id,
            version: nuevaVersion,
            operacion: 'UPDATE',
            autor: req.usuario.sub,
            campos: camposDelCambio.map((campo) => ({
                campo,
                anterior: textoDe(persona[campo]),
                nuevo: textoDe(cambios[campo]),
            })),
        });

        await cliente.query('COMMIT');

        return res.json({
            persona: actualizada.rows[0],
            mergeado: decision.tipo === 'MERGE',
            rechazados,
        });
    } catch (error) {
        await cliente.query('ROLLBACK').catch(() => {});

        // 23505: el documento nuevo ya pertenece a otra persona. Reintentar no
        // lo va a arreglar nunca, y el cliente necesita saber esa diferencia:
        // un 500 lo haría reintentar cuatro veces y descartar el cambio.
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
        return res.status(500).json({ error: 'Error al editar la persona' });
    } finally {
        cliente.release();
    }
};

// ===========================================================================
// DELETE /api/personas/:id — baja lógica, con validación de versión
// ===========================================================================
const eliminarPersona = async (req, res) => {
    const { id } = req.params;
    const versionBase = Number(
        req.body && req.body.version_base !== undefined ? req.body.version_base : NaN
    );

    if (!Number.isInteger(versionBase) || versionBase < 1) {
        return res.status(400).json({ error: 'Hay que indicar version_base' });
    }

    const cliente = await pool.connect();

    try {
        await cliente.query('BEGIN');

        // El sistema anterior daba de baja sin mirar la versión: una baja
        // podía borrar un registro que otro acababa de corregir, sin que
        // nadie se enterara.
        const baja = await cliente.query(
            `UPDATE personas
                SET deleted_at = now(), version = version + 1
              WHERE id = $1 AND version = $2 AND deleted_at IS NULL
              RETURNING ${CAMPOS_PUBLICOS}`,
            [id, versionBase]
        );

        if (baja.rows.length === 0) {
            const existe = await cliente.query(
                'SELECT version, deleted_at FROM personas WHERE id = $1',
                [id]
            );
            await cliente.query('ROLLBACK');

            if (existe.rows.length === 0) {
                return res.status(404).json({ error: 'Persona no encontrada' });
            }
            if (existe.rows[0].deleted_at) {
                // Reintento de la outbox sobre algo ya dado de baja.
                return res.status(200).json({ ya_eliminada: true });
            }
            return res.status(409).json({
                error: 'La persona cambió desde que la descargó',
                codigo: 'CONFLICTO_VERSION',
                version_servidor: existe.rows[0].version,
            });
        }

        await registrarEnHistorial(cliente, {
            personaId: id,
            version: baja.rows[0].version,
            operacion: 'DELETE',
            autor: req.usuario.sub,
        });

        await cliente.query('COMMIT');
        return res.json({ persona: baja.rows[0] });
    } catch (error) {
        await cliente.query('ROLLBACK').catch(() => {});
        if (error.code === '22P02') {
            return res.status(404).json({ error: 'Persona no encontrada' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al dar de baja la persona' });
    } finally {
        cliente.release();
    }
};

// ===========================================================================
// GET /api/personas/sync?desde=<cambio_seq> — descarga incremental
// ===========================================================================
const obtenerCambios = async (req, res) => {
    const desde = Number(req.query.desde || 0);
    const limite = Math.min(
        Number(req.query.limite) || LIMITE_SYNC_POR_DEFECTO,
        LIMITE_SYNC_MAXIMO
    );

    if (!Number.isFinite(desde) || desde < 0) {
        return res.status(400).json({ error: 'El cursor "desde" no es válido' });
    }

    // El filtro por rol cierra la fuga del sistema anterior, donde cualquier
    // sesión autenticada se bajaba la población entera.
    const esAdmin = req.usuario.rol === 'admin';
    const condicion = esAdmin ? '' : 'AND creado_por = $3';
    const parametros = esAdmin ? [desde, limite] : [desde, limite, req.usuario.sub];

    try {
        // Se incluyen las filas dadas de baja a propósito: es la única forma
        // de que el dispositivo se entere de que tiene que borrarlas.
        const resultado = await pool.query(
            `SELECT ${CAMPOS_PUBLICOS} FROM personas
              WHERE cambio_seq > $1 ${condicion}
              ORDER BY cambio_seq
              LIMIT $2`,
            parametros
        );

        const filas = resultado.rows;
        const cursor = filas.length > 0 ? filas[filas.length - 1].cambio_seq : desde;

        return res.json({
            personas: filas,
            cursor,
            // Si vino una página completa, es probable que haya más. El
            // cliente tiene que volver a pedir antes de dar por terminada la
            // sincronización.
            hay_mas: filas.length === limite,
        });
    } catch (error) {
        console.error(error);
        return res.status(500).json({ error: 'Error al obtener los cambios' });
    }
};

// ===========================================================================
// GET /api/personas — listado del admin, con quién registró a cada una
// ===========================================================================
const listarPersonas = async (req, res) => {
    const limite = Math.min(Number(req.query.limite) || 100, 500);
    const desplazamiento = Math.max(Number(req.query.desde) || 0, 0);

    try {
        const resultado = await pool.query(
            `SELECT ${CAMPOS_PUBLICOS.split(',')
                .map((c) => `p.${c.trim()}`)
                .join(', ')},
                    u.usuario AS registrado_por
               FROM personas p
               JOIN usuarios u ON u.id = p.creado_por
              WHERE p.deleted_at IS NULL
              ORDER BY p.primer_apellido, p.primer_nombre
              LIMIT $1 OFFSET $2`,
            [limite, desplazamiento]
        );

        const total = await pool.query(
            'SELECT count(*)::int AS cantidad FROM personas WHERE deleted_at IS NULL'
        );

        return res.json({
            personas: resultado.rows,
            total: total.rows[0].cantidad,
        });
    } catch (error) {
        console.error(error);
        return res.status(500).json({ error: 'Error al obtener las personas' });
    }
};

// ===========================================================================
// GET /api/personas/:id
// ===========================================================================
const obtenerPersona = async (req, res) => {
    const { id } = req.params;

    // Esta ruta la comparten los tres roles, así que la autorización se
    // resuelve acá y no en un middleware: el de propiedad autoriza ESCRITURA y
    // rechaza al rol usuario, que aquí sí tiene que poder entrar.
    if (req.usuario.rol === 'usuario' && req.usuario.sub !== id) {
        return res.status(403).json({ error: 'Solo puede consultar su propio registro' });
    }

    try {
        const resultado = await pool.query(
            `SELECT ${CAMPOS_PUBLICOS} FROM personas WHERE id = $1 AND deleted_at IS NULL`,
            [id]
        );

        if (resultado.rows.length === 0) {
            return res.status(404).json({ error: 'Persona no encontrada' });
        }

        const persona = resultado.rows[0];

        // El registrador tampoco puede LEER lo que no registró. Sin esto,
        // /sync le entrega solo lo suyo pero pedir por id le daría cualquier
        // registro del padrón: la restricción sería decorativa.
        if (req.usuario.rol === 'registrador' && persona.creado_por !== req.usuario.sub) {
            return res.status(404).json({ error: 'Persona no encontrada' });
        }

        return res.json(persona);
    } catch (error) {
        if (error.code === '22P02') {
            return res.status(404).json({ error: 'Persona no encontrada' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al obtener la persona' });
    }
};

// ===========================================================================
// GET /api/personas/:id/historial — auditoría completa, sin recortes
// ===========================================================================
const obtenerHistorial = async (req, res) => {
    const { id } = req.params;

    try {
        const resultado = await pool.query(
            // conflicto_id viaja en la respuesta: es lo que le permite al
            // admin distinguir un cambio corriente de uno que salió de
            // resolver un conflicto, y saltar al intento original.
            `SELECT h.id, h.version, h.operacion, h.campo,
                    h.valor_anterior, h.valor_nuevo, h.realizado_en, h.conflicto_id,
                    u.usuario AS realizado_por
               FROM historial_cambios h
               JOIN usuarios u ON u.id = h.realizado_por
              WHERE h.persona_id = $1
              ORDER BY h.version DESC, h.id DESC`,
            [id]
        );

        return res.json(resultado.rows);
    } catch (error) {
        if (error.code === '22P02') {
            return res.status(404).json({ error: 'Persona no encontrada' });
        }
        console.error(error);
        return res.status(500).json({ error: 'Error al obtener el historial' });
    }
};

module.exports = {
    crearPersona,
    editarPersona,
    eliminarPersona,
    obtenerCambios,
    listarPersonas,
    obtenerPersona,
    obtenerHistorial,
};
