package com.tuapp.bancopersonas.data.sync

import android.util.Log
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
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
}