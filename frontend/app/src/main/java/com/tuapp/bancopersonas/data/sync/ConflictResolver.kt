package com.tuapp.bancopersonas.data.sync

import android.util.Log
import com.google.gson.Gson
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.local.entity.OutboxEntity
import com.tuapp.bancopersonas.data.remote.PersonaApi
import com.tuapp.bancopersonas.data.remote.dto.PersonaDto
import javax.inject.Inject

class ConflictResolver @Inject constructor(
    private val personaDao: PersonaDao,
    private val outboxDao: OutboxDao,
    private val personaApi: PersonaApi
) {
    private val gson = Gson()

    // Llamado cuando el servidor responde 409 en un UPDATE
    suspend fun resolver(operacion: OutboxEntity) {
        Log.d("SYNC_DEBUG", "Iniciando resolución de conflicto para ${operacion.personaId}")
        
        // Paso 1: obtener la versión actual del servidor
        val respuestaServidor = try {
            personaApi.obtenerCambios(since = "1970-01-01")
        } catch (e: Exception) {
            Log.e("SYNC_DEBUG", "Error al obtener cambios del servidor: ${e.message}")
            personaDao.actualizarSyncStatus(operacion.personaId, "CONFLICT")
            return
        }

        if (!respuestaServidor.isSuccessful) {
            Log.e("SYNC_DEBUG", "Respuesta no exitosa al obtener cambios: ${respuestaServidor.code()}")
            personaDao.actualizarSyncStatus(operacion.personaId, "CONFLICT")
            return
        }

        val personaEnServidor = respuestaServidor.body()
            ?.firstOrNull { it.id == operacion.personaId }

        if (personaEnServidor == null) {
            Log.d("SYNC_DEBUG", "Persona ${operacion.personaId} no encontrada en servidor, asumiendo eliminación remota")
            personaDao.marcarComoEliminado(operacion.personaId, System.currentTimeMillis())
            outboxDao.eliminar(operacion.outboxId)
            return
        }

        Log.d("SYNC_DEBUG", "Conflicto detectado: Versión local intentó usar base v?, Servidor tiene v${personaEnServidor.version}")

        // Paso 2: Corregir el payload del outbox para usar la nueva versión base del servidor
        // Esto permite que el próximo intento de SyncWorker sea exitoso (Last Write Wins)
        try {
            val oldPayloadMap = gson.fromJson(operacion.payload, Map::class.java)
            val nuevoPayloadMap = oldPayloadMap.toMutableMap().apply {
                this["version"] = personaEnServidor.version
            }
            val nuevoPayload = gson.toJson(nuevoPayloadMap)
            
            outboxDao.actualizarPayload(operacion.outboxId, nuevoPayload)
            
            // Actualizamos la versión local también para que coincida con la nueva base
            // y marcamos como PENDING para que el usuario sepa que aún se está intentando
            personaDao.actualizarVersionYStatus(
                operacion.personaId, 
                personaEnServidor.version + 1, // +1 porque localmente ya tenemos el cambio aplicado
                "PENDING"
            )
            
            Log.d("SYNC_DEBUG", "Payload actualizado con versión ${personaEnServidor.version}. El cambio se reintentará.")
        } catch (e: Exception) {
            Log.e("SYNC_DEBUG", "Error al procesar el JSON del payload: ${e.message}")
            personaDao.actualizarSyncStatus(operacion.personaId, "CONFLICT")
        }
    }
}