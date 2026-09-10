package com.tuapp.bancopersonas.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tuapp.bancopersonas.data.local.dao.HistorialDao
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.data.local.entity.OutboxEntity
import com.tuapp.bancopersonas.data.mapper.toAltaDto
import com.tuapp.bancopersonas.data.mapper.toDomain
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.data.remote.PersonaApi
import com.tuapp.bancopersonas.data.remote.dto.EdicionPersonaDto
import com.tuapp.bancopersonas.domain.model.CamposPersona
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.model.TipoDocumento
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PersonaRepositoryImpl @Inject constructor(
    private val personaDao: PersonaDao,
    private val outboxDao: OutboxDao,
    private val historialDao: HistorialDao,
    private val api: PersonaApi,
) : PersonaRepository {

    private val gson = Gson()

    override fun observarPersonas(): Flow<List<Persona>> =
        personaDao.observarTodas().map { lista -> lista.map { it.toDomain() } }

    override fun observarPersona(id: String): Flow<Persona?> =
        personaDao.observarUna(id).map { it?.toDomain() }

    override suspend fun obtenerPorId(id: String): Persona? =
        personaDao.obtenerPorId(id)?.toDomain()

    override suspend fun buscarPorDocumento(tipo: TipoDocumento, numero: String): Persona? =
        personaDao.obtenerPorDocumento(tipo.name, numero.trim())?.toDomain()

    // ======================================================================
    // Alta
    // ======================================================================
    override suspend fun crearPersona(persona: Persona) {
        val entity = persona.toEntity().copy(syncStatus = SyncStatus.PENDING.name)
        personaDao.guardar(entity)

        outboxDao.encolar(
            OutboxEntity(
                personaId = entity.id,
                operacion = OPERACION_CREAR,
                payload = gson.toJson(entity.toAltaDto()),
                creadoEn = System.currentTimeMillis(),
            )
        )
        Log.d(TAG, "Alta ${entity.id} encolada")
    }

    // ======================================================================
    // Edición
    // ======================================================================
    override suspend fun editarPersona(persona: Persona) {
        val actual = personaDao.obtenerPorId(persona.id) ?: return

        // Solo lo que cambió de verdad. El formulario devuelve el registro
        // entero, y mandarlo completo convertiría cualquier edición simultánea
        // en un choque que el servidor sabría evitar.
        val cambios = CamposPersona.diferencias(actual, persona)
        if (cambios.isEmpty()) return

        // La fila local muestra ya los valores nuevos, pero `version` NO se
        // toca: sigue siendo la última versión que emitió el servidor, y es
        // contra esa que se pide el merge. Incrementarla acá — como hacía el
        // código anterior — hacía que el cliente inventara versiones que el
        // servidor nunca había emitido.
        personaDao.guardar(
            persona.toEntity(
                cambioSeq = actual.cambioSeq,
                deletedAt = actual.deletedAt,
            ).copy(
                version = actual.version,
                syncStatus = SyncStatus.PENDING.name,
                conflictoId = null,
            )
        )

        // Un alta que todavía no salió del teléfono no se edita con un UPDATE:
        // para el servidor esa persona no existe. Se corrige el alta misma.
        val altaPendiente = outboxDao.pendientePara(persona.id, OPERACION_CREAR)
        if (altaPendiente != null) {
            val entityActualizada = personaDao.obtenerPorId(persona.id) ?: return
            outboxDao.actualizarPayload(
                altaPendiente.outboxId,
                gson.toJson(entityActualizada.toAltaDto())
            )
            return
        }

        // Si ya había una edición esperando, se unifican en una sola.
        //
        // Dos UPDATE sueltos viajarían con la misma version_base: el servidor
        // aplicaría el primero y vería el segundo como conflicto contra un
        // cambio que hizo este mismo registrador. Su propio trabajo rechazado
        // por sí mismo.
        val edicionPendiente = outboxDao.pendientePara(persona.id, OPERACION_EDITAR)

        if (edicionPendiente != null) {
            val previa = leerEdicion(edicionPendiente.payload)
            outboxDao.actualizarPayload(
                edicionPendiente.outboxId,
                gson.toJson(
                    EdicionPersonaDto(
                        // Se conserva la version_base de la PRIMERA edición:
                        // es la última que el servidor confirmó.
                        versionBase = previa?.versionBase ?: actual.version,
                        cambios = (previa?.cambios ?: emptyMap()) + cambios,
                    )
                )
            )
            return
        }

        outboxDao.encolar(
            OutboxEntity(
                personaId = persona.id,
                operacion = OPERACION_EDITAR,
                payload = gson.toJson(
                    EdicionPersonaDto(versionBase = actual.version, cambios = cambios)
                ),
                creadoEn = System.currentTimeMillis(),
            )
        )
    }

    // ======================================================================
    // Baja
    // ======================================================================
    override suspend fun eliminarPersona(id: String) {
        val actual = personaDao.obtenerPorId(id) ?: return
        val pendientes = outboxDao.pendientesDe(id)

        // Un alta que nunca salió del teléfono: para el servidor esta persona
        // no existió jamás. Mandarle una baja daría 404 y dejaría la cola
        // trabada. Se borra local y se limpia la cola, y listo.
        if (pendientes.any { it.operacion == OPERACION_CREAR }) {
            outboxDao.eliminarDe(id)
            personaDao.eliminarFisicamente(id)
            Log.d(TAG, "Alta $id descartada antes de sincronizar")
            return
        }

        // Las ediciones pendientes ya no tienen sentido: se va a dar de baja.
        outboxDao.eliminarDe(id)

        personaDao.marcarComoEliminado(id, System.currentTimeMillis())

        outboxDao.encolar(
            OutboxEntity(
                personaId = id,
                operacion = OPERACION_ELIMINAR,
                payload = gson.toJson(mapOf("version_base" to actual.version)),
                creadoEn = System.currentTimeMillis(),
            )
        )
    }

    // ======================================================================
    // Historial
    // ======================================================================
    override suspend fun sincronizarHistorial(personaId: String) {
        try {
            val respuesta = api.obtenerHistorial(personaId)
            if (!respuesta.isSuccessful) return

            val entradas = respuesta.body().orEmpty().map { it.toEntity(personaId) }
            historialDao.limpiarHistorial(personaId)
            historialDao.guardarVarios(entradas)
        } catch (e: Exception) {
            // Sin conexión se muestra lo que ya está guardado. No es un error
            // que deba interrumpir a nadie.
            Log.d(TAG, "No se pudo actualizar el historial de $personaId: ${e.message}")
        }
    }

    override fun observarHistorial(personaId: String): Flow<List<HistorialEntity>> =
        historialDao.observarHistorial(personaId)

    override suspend fun revertirCambio(persona: Persona, historial: HistorialEntity) {
        val campo = historial.campo ?: return
        val anterior = historial.valorAnterior

        // Revertir es una edición más: se encola como cualquier otra y pasa
        // por el mismo merge. No es un camino aparte que pueda pisar a nadie.
        val revertida = when (campo) {
            CamposPersona.PRIMER_NOMBRE -> persona.copy(primerNombre = anterior.orEmpty())
            CamposPersona.SEGUNDO_NOMBRE -> persona.copy(segundoNombre = anterior)
            CamposPersona.PRIMER_APELLIDO -> persona.copy(primerApellido = anterior.orEmpty())
            CamposPersona.SEGUNDO_APELLIDO -> persona.copy(segundoApellido = anterior)
            CamposPersona.NUMERO_DOCUMENTO -> persona.copy(numeroDocumento = anterior.orEmpty())
            CamposPersona.TIPO_DOCUMENTO ->
                TipoDocumento.desde(anterior)?.let { persona.copy(tipoDocumento = it) }
            CamposPersona.FECHA_NACIMIENTO -> persona.copy(fechaNacimiento = anterior.orEmpty())
            CamposPersona.TELEFONO -> persona.copy(telefono = anterior)
            CamposPersona.SEXO ->
                com.tuapp.bancopersonas.domain.model.Sexo.desde(anterior)
                    ?.let { persona.copy(sexo = it) }
            else -> null
        } ?: return

        editarPersona(revertida)
    }

    // ======================================================================
    // Sesión
    // ======================================================================
    override suspend fun cambiosSinEnviar(): Int = outboxDao.contarPendientes()

    override suspend fun limpiarDatosLocales(): Result<Unit> {
        val pendientes = outboxDao.contarPendientes()

        if (pendientes > 0) {
            return Result.failure(
                IllegalStateException(
                    "Quedan $pendientes cambios sin enviar. Conectate para sincronizarlos " +
                        "antes de cerrar sesión."
                )
            )
        }

        personaDao.limpiarTodo()
        historialDao.limpiarTodo()
        return Result.success(Unit)
    }

    private fun leerEdicion(payload: String): EdicionPersonaDto? =
        runCatching {
            gson.fromJson<EdicionPersonaDto>(
                payload,
                object : TypeToken<EdicionPersonaDto>() {}.type
            )
        }.getOrNull()

    companion object {
        const val OPERACION_CREAR = "CREATE"
        const val OPERACION_EDITAR = "UPDATE"
        const val OPERACION_ELIMINAR = "DELETE"
        private const val TAG = "SYNC_DEBUG"
    }
}
