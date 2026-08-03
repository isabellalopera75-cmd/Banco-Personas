package com.tuapp.bancopersonas.data.repository

import android.util.Log
import com.google.gson.Gson
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.local.entity.OutboxEntity
import com.tuapp.bancopersonas.data.mapper.toDomain
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class PersonaRepositoryImpl @Inject constructor(
    private val personaDao: PersonaDao,
    private val outboxDao: OutboxDao
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

        // Armar el payload JSON que el servidor espera
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
        // La versión que viene en el modelo 'persona' es la versión base que el usuario editó.
        // Incrementamos localmente para marcar el cambio pendiente.
        val entity = persona.toEntity(versionOverride = persona.version + 1)
        personaDao.guardar(entity)

        // El payload manda la versión base (persona.version) para que el servidor
        // detecte conflictos si la versión en el backend ya es mayor.
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
}