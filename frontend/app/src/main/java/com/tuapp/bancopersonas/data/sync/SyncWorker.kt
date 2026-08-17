package com.tuapp.bancopersonas.data.sync

import android.util.Log
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.mapper.toDomain
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.data.remote.PersonaApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val personaApi: PersonaApi,
    private val outboxDao: OutboxDao,
    private val personaDao: PersonaDao,
    private val conflictResolver: ConflictResolver
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("SYNC_DEBUG", "SyncWorker iniciado")

        // FASE 1: PULL (Descargar cambios del servidor)
        try {
            descargarCambios()
        } catch (e: Exception) {
            Log.e("SYNC_DEBUG", "Error en fase PULL: ${e.message}")
            // No retornamos falla aquí para intentar al menos el PUSH
        }

        // FASE 2: PUSH (Enviar cambios locales)
        val pendientes = outboxDao.obtenerPendientes()
        Log.d("SYNC_DEBUG", "Pendientes en outbox: ${pendientes.size}")

        for (operacion in pendientes) {
            Log.d("SYNC_DEBUG", "Procesando operacion ${operacion.outboxId}: ${operacion.operacion} para persona ${operacion.personaId}")
            try {
                val requestBody = operacion.payload
                    .toRequestBody("application/json".toMediaType())

                val respuesta = when (operacion.operacion) {
                    "CREATE" -> personaApi.crear(requestBody)
                    "UPDATE" -> personaApi.actualizar(operacion.personaId, requestBody)
                    "DELETE" -> personaApi.eliminar(operacion.personaId)
                    else -> null
                }

                Log.d("SYNC_DEBUG", "Respuesta operacion ${operacion.outboxId}: código ${respuesta?.code()}")

                when (respuesta?.code()) {
                    200, 201, 204 -> {
                        personaDao.actualizarSyncStatus(operacion.personaId, "SYNCED")
                        outboxDao.eliminar(operacion.outboxId)
                        Log.d("SYNC_DEBUG", "Operacion ${operacion.outboxId} sincronizada exitosamente")
                    }
                    409 -> {
                        // Delegamos la resolución al ConflictResolver
                        conflictResolver.resolver(operacion)
                        Log.d("SYNC_DEBUG", "Conflicto en operacion ${operacion.outboxId} delegado al ConflictResolver")
                    }
                    null -> {
                        outboxDao.incrementarIntentos(operacion.outboxId)
                        if (operacion.intentos >= 4) {
                            personaDao.actualizarSyncStatus(operacion.personaId, "CONFLICT")
                            outboxDao.eliminar(operacion.outboxId)
                        }
                        Log.d("SYNC_DEBUG", "Operacion ${operacion.outboxId} sin respuesta, reintentando")
                        return Result.retry()
                    }
                    else -> {
                        outboxDao.incrementarIntentos(operacion.outboxId)
                        if (operacion.intentos >= 4) {
                            personaDao.actualizarSyncStatus(operacion.personaId, "CONFLICT")
                            outboxDao.eliminar(operacion.outboxId)
                        }
                        Log.d("SYNC_DEBUG", "Error desconocido en operacion ${operacion.outboxId}: ${respuesta?.code()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SYNC_DEBUG", "Excepción en operacion ${operacion.outboxId}: ${e.message}", e)
                outboxDao.incrementarIntentos(operacion.outboxId)
                return Result.retry()
            }
        }
        Log.d("SYNC_DEBUG", "SyncWorker terminado exitosamente")
        return Result.success()
    }

    private suspend fun descargarCambios() {
        Log.d("SYNC_DEBUG", "Iniciando fase PULL...")
        // En una app real usaríamos el timestamp del último cambio descargado
        val respuesta = personaApi.obtenerCambios(since = "1970-01-01")
        
        if (respuesta.isSuccessful) {
            val remotos = respuesta.body() ?: emptyList()
            Log.d("SYNC_DEBUG", "Descargados ${remotos.size} registros del servidor")
            
            remotos.forEach { dto ->
                val local = personaDao.obtenerPorId(dto.id)
                
                // REGLA DE ORO: Solo sobrescribimos si el dato local ya está sincronizado.
                // Si está PENDING o CONFLICT, respetamos el trabajo del usuario local.
                if (local == null || local.syncStatus == "SYNCED") {
                    personaDao.guardar(dto.toDomain().toEntity())
                } else {
                    Log.d("SYNC_DEBUG", "Omitiendo descarga para ${dto.nombre}: tiene cambios locales pendientes")
                }
            }
        } else {
            Log.e("SYNC_DEBUG", "Error al descargar cambios: código ${respuesta.code()}")
        }
    }
}
