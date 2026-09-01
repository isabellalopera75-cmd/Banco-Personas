package com.tuapp.bancopersonas.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.tuapp.bancopersonas.data.local.SessionManager
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.local.entity.OutboxEntity
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.data.remote.AuthApi
import com.tuapp.bancopersonas.data.remote.PersonaApi
import com.tuapp.bancopersonas.data.remote.dto.RegisterRequest
import com.tuapp.bancopersonas.data.remote.dto.RegistroPendiente
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val personaApi: PersonaApi,
    private val authApi: AuthApi,
    private val outboxDao: OutboxDao,
    private val personaDao: PersonaDao,
    private val sessionManager: SessionManager,
    private val conflictResolver: ConflictResolver
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker iniciado")
        var huboFalloTransitorio = false

        // FASE 1: PULL. Requiere sesión, porque /api/personas/sync está protegido.
        if (sessionManager.haySesion) {
            try {
                descargarCambios()
            } catch (e: Exception) {
                Log.e(TAG, "Error en fase PULL: ${e.message}")
                huboFalloTransitorio = true
            }
        }

        // FASE 2: PUSH.
        val pendientes = outboxDao.obtenerPendientes()
        Log.d(TAG, "Pendientes en outbox: ${pendientes.size}")

        for (operacion in pendientes) {
            try {
                // Un null acá significa que la operación ya se resolvió y se
                // sacó de la cola; no es un fallo.
                val respuesta = ejecutar(operacion) ?: continue

                when (respuesta.code()) {
                    200, 201, 204 -> confirmar(operacion)

                    401 -> {
                        // La sesión venció. Nada de lo que sigue va a funcionar,
                        // así que se corta acá y se conserva la cola intacta.
                        Log.e(TAG, "Sesión vencida durante la sincronización")
                        huboFalloTransitorio = true
                        break
                    }

                    409 -> resolverConflicto(operacion)

                    else -> {
                        Log.e(TAG, "Operación ${operacion.outboxId} falló: ${respuesta.code()}")
                        registrarIntentoFallido(operacion)
                    }
                }
            } catch (e: Exception) {
                // Excepción = problema de red. Se reintenta más tarde, pero se
                // sigue procesando el resto de la cola: antes, un solo fallo
                // dejaba sin enviar todas las operaciones siguientes.
                Log.e(TAG, "Excepción en operación ${operacion.outboxId}: ${e.message}")
                outboxDao.incrementarIntentos(operacion.outboxId)
                huboFalloTransitorio = true
            }
        }

        Log.d(TAG, "SyncWorker terminado (reintento pendiente: $huboFalloTransitorio)")
        return if (huboFalloTransitorio) Result.retry() else Result.success()
    }

    private suspend fun ejecutar(operacion: OutboxEntity): Response<*>? = when (operacion.operacion) {
        "CREATE" -> ejecutarAlta(operacion)
        "UPDATE" -> personaApi.actualizar(
            operacion.personaId,
            operacion.payload.toRequestBody(JSON)
        )
        "DELETE" -> personaApi.eliminar(operacion.personaId)
        else -> {
            Log.e(TAG, "Operación desconocida '${operacion.operacion}', se descarta")
            outboxDao.eliminar(operacion.outboxId)
            null
        }
    }

    /**
     * El alta va a /api/auth/register, que es pública: quien se registra
     * todavía no tiene token. La contraseña se recupera del almacenamiento
     * cifrado, nunca de la outbox.
     */
    private suspend fun ejecutarAlta(operacion: OutboxEntity): Response<*>? {
        val password = sessionManager.obtenerPasswordPendiente(operacion.personaId)

        if (password == null) {
            // Reintentar no va a hacer aparecer la contraseña.
            Log.e(TAG, "Alta ${operacion.personaId} sin contraseña guardada, se descarta")
            personaDao.actualizarSyncStatus(operacion.personaId, "CONFLICT")
            outboxDao.eliminar(operacion.outboxId)
            return null
        }

        val pendiente = gson.fromJson(operacion.payload, RegistroPendiente::class.java)

        return authApi.register(
            RegisterRequest(
                id = pendiente.id,
                nombre = pendiente.nombre,
                documento = pendiente.documento,
                telefono = pendiente.telefono,
                password = password
            )
        )
    }

    private suspend fun confirmar(operacion: OutboxEntity) {
        if (operacion.operacion == "CREATE") {
            // El servidor ya tiene el alta: la contraseña en claro deja de
            // ser necesaria en el dispositivo.
            //
            // El token que devuelve el registro se descarta a propósito: esta
            // sincronización corre en segundo plano y podría haber otra sesión
            // abierta. La sesión se inicia desde la pantalla de login.
            sessionManager.borrarPasswordPendiente(operacion.personaId)
        }
        personaDao.actualizarSyncStatus(operacion.personaId, "SYNCED")
        outboxDao.eliminar(operacion.outboxId)
        Log.d(TAG, "Operación ${operacion.outboxId} sincronizada")
    }

    private suspend fun resolverConflicto(operacion: OutboxEntity) {
        if (operacion.operacion == "CREATE") {
            // 409 en un alta significa documento ya registrado. Reintentar da
            // siempre el mismo resultado, así que se marca y se saca de la cola.
            Log.e(TAG, "El documento del alta ${operacion.personaId} ya está registrado")
            personaDao.actualizarSyncStatus(operacion.personaId, "CONFLICT")
            sessionManager.borrarPasswordPendiente(operacion.personaId)
            outboxDao.eliminar(operacion.outboxId)
            return
        }
        conflictResolver.resolver(operacion)
    }

    private suspend fun registrarIntentoFallido(operacion: OutboxEntity) {
        outboxDao.incrementarIntentos(operacion.outboxId)
        if (operacion.intentos >= MAX_INTENTOS) {
            personaDao.actualizarSyncStatus(operacion.personaId, "CONFLICT")
            outboxDao.eliminar(operacion.outboxId)
            Log.e(TAG, "Operación ${operacion.outboxId} agotó los reintentos")
        }
    }

    private suspend fun descargarCambios() {
        val desde = sessionManager.ultimaSincronizacion ?: INICIO_DE_LOS_TIEMPOS
        val respuesta = personaApi.obtenerCambios(since = desde)

        if (!respuesta.isSuccessful) {
            Log.e(TAG, "Error al descargar cambios: ${respuesta.code()}")
            return
        }

        val remotos = respuesta.body() ?: emptyList()
        Log.d(TAG, "Descargados ${remotos.size} registros desde $desde")

        remotos.forEach { dto ->
            val local = personaDao.obtenerPorId(dto.id)

            // Solo se pisa lo local si ya estaba sincronizado. Si hay un cambio
            // del usuario sin subir, se respeta: el trabajo local no se pierde.
            if (local == null || local.syncStatus == "SYNCED") {
                personaDao.guardar(dto.toEntity())
            } else {
                Log.d(TAG, "Se omite ${dto.id}: tiene cambios locales pendientes")
            }
        }

        // El cursor avanza con la marca de tiempo del servidor, no con la hora
        // del teléfono: un reloj local corrido saltearía cambios en silencio.
        remotos.mapNotNull { it.updatedAt }.maxOrNull()?.let {
            sessionManager.ultimaSincronizacion = it
        }
    }

    private companion object {
        const val TAG = "SYNC_DEBUG"
        const val MAX_INTENTOS = 4
        const val INICIO_DE_LOS_TIEMPOS = "1970-01-01T00:00:00.000Z"
        val JSON = "application/json".toMediaType()
    }
}
