package com.tuapp.bancopersonas.data.repository

import android.util.Log
import com.google.gson.Gson
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.local.dao.HistorialDao
import com.tuapp.bancopersonas.data.local.entity.OutboxEntity
import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.data.mapper.toDomain
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.data.remote.PersonaApi
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class PersonaRepositoryImpl @Inject constructor(
    private val personaDao: PersonaDao,
    private val outboxDao: OutboxDao,
    private val historialDao: HistorialDao,
    private val api: PersonaApi
) : PersonaRepository {

    private val gson = Gson()

    override fun observarPersonas(): Flow<List<Persona>> {
        return personaDao.observarTodas().map { lista ->
            lista.map { it.toDomain() }
        }
    }

    override suspend fun crearPersona(persona: Persona) {
        Log.d("SYNC_DEBUG", "Repositorio: Creando persona ${persona.nombre}")
        val nuevaPersona = persona.copy(id = UUID.randomUUID().toString(), version = 1)
        val entity = nuevaPersona.toEntity()
        personaDao.guardar(entity)
        Log.d("SYNC_DEBUG", "Repositorio: Persona guardada en DAO local")

        val payload = gson.toJson(mapOf(
            "id" to entity.id,
            "nombre" to entity.nombre,
            "documento" to entity.documento,
            "telefono" to entity.telefono
        ))

        outboxDao.encolar(OutboxEntity(
            personaId = entity.id,
            operacion = "CREATE",
            payload = payload,
            creadoEn = System.currentTimeMillis()
        ))
        Log.d("SYNC_DEBUG", "Repositorio: Operacion CREATE encolada en outbox")
    }

    override suspend fun editarPersona(persona: Persona) {
        // Marcamos localmente como PENDING para que la UI sepa que hay cambios sin subir
        val personaPendiente = persona.copy(syncStatus = SyncStatus.PENDING)
        val entity = personaPendiente.toEntity(versionOverride = persona.version + 1)
        personaDao.guardar(entity)

        val payload = gson.toJson(mapOf(
            "nombre" to entity.nombre,
            "documento" to entity.documento,
            "telefono" to entity.telefono,
            "version" to persona.version
        ))

        outboxDao.encolar(OutboxEntity(
            personaId = entity.id,
            operacion = "UPDATE",
            payload = payload,
            creadoEn = System.currentTimeMillis()
        ))
    }

    override suspend fun eliminarPersona(id: String) {
        personaDao.marcarComoEliminado(id, System.currentTimeMillis())

        val payload = gson.toJson(mapOf("id" to id))

        outboxDao.encolar(OutboxEntity(
            personaId = id,
            operacion = "DELETE",
            payload = payload,
            creadoEn = System.currentTimeMillis()
        ))
    }

    override suspend fun sincronizarHistorial(personaId: String) {
        try {
            val response = api.obtenerHistorial(personaId)
            if (response.isSuccessful) {
                response.body()?.let { historiales ->
                    historialDao.limpiarHistorial(personaId)
                    historialDao.guardarVarios(historiales)
                }
            }
        } catch (e: Exception) {
            Log.e("SYNC_DEBUG", "Error sincronizando historial offline: ${e.message}")
        }
    }

    override fun observarHistorial(personaId: String): Flow<List<HistorialEntity>> {
        return historialDao.observarHistorial(personaId)
    }

    override suspend fun revertirHistorial(persona: Persona, historial: HistorialEntity) {
        // Al revertir, creamos una nueva edición con los datos antiguos
        val personaRevertida = persona.copy(
            nombre = historial.nombreAnterior,
            documento = historial.documentoAnterior,
            telefono = historial.telefonoAnterior
        )
        editarPersona(personaRevertida)
    }

    override suspend fun getGanadorSemana(): Result<Persona> {
        return try {
            val response = api.getGanadorSemana()
            if (response.isSuccessful) {
                response.body()?.let { dto ->
                    Result.success(dto.toDomain())
                } ?: Result.failure(Exception("Ganador vacio"))
            } else {
                Result.failure(Exception("Error HTTP: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}