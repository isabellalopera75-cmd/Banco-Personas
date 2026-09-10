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
import com.tuapp.bancopersonas.data.remote.ConflictoApi
import com.tuapp.bancopersonas.data.remote.PersonaApi
import com.tuapp.bancopersonas.data.remote.dto.AltaPersonaDto
import com.tuapp.bancopersonas.data.remote.dto.BajaPersonaDto
import com.tuapp.bancopersonas.data.remote.dto.EdicionPersonaDto
import com.tuapp.bancopersonas.data.remote.dto.ErrorApiDto
import com.tuapp.bancopersonas.data.repository.PersonaRepositoryImpl
import com.tuapp.bancopersonas.domain.model.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import retrofit2.Response

/**
 * Sincronización en dos fases: primero baja lo que cambió en el servidor,
 * después sube lo que este dispositivo hizo sin conexión.
 *
 * La diferencia grande con la versión anterior está en el manejo del 409.
 * Antes había un `ConflictResolver` que ante un conflicto pedía la versión del
 * servidor, reescribía el payload con esa versión y reintentaba: Last Write
 * Wins ciego, que pisaba en silencio el cambio de otra persona. Ese archivo se
 * eliminó.
 *
 * Ahora el servidor resuelve o guarda. Si puede mergear, mergea; si no, deja
 * el intento en su tabla de conflictos y responde con el id. Acá lo único que
 * queda por hacer es marcar el registro como EN_REVISION y sacarlo de la cola,
 * y eso es seguro precisamente porque el dato ya está guardado del otro lado.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val personaApi: PersonaApi,
    private val conflictoApi: ConflictoApi,
    private val outboxDao: OutboxDao,
    private val personaDao: PersonaDao,
    private val sessionManager: SessionManager,
) : CoroutineWorker(context, params) {

    private val gson = Gson()

    override suspend fun doWork(): Result {
        // Sin token no hay nada que hacer: todas las rutas del padrón exigen
        // sesión. Un registrador trabajando sin conexión cae acá y su cola
        // queda intacta esperando señal.
        if (!sessionManager.puedeSincronizar) {
            Log.d(TAG, "Sin token: se pospone la sincronización")
            return Result.retry()
        }

        var huboFalloTransitorio = false

        try {
            descargarCambios()
        } catch (e: Exception) {
            Log.e(TAG, "Fallo en la descarga: ${e.message}")
            huboFalloTransitorio = true
        }

        val pendientes = outboxDao.obtenerPendientes()
        Log.d(TAG, "Operaciones en cola: ${pendientes.size}")

        for (operacion in pendientes) {
            try {
                if (!enviar(operacion)) break
            } catch (e: Exception) {
                // Excepción es problema de red. Se reintenta más tarde, pero
                // se sigue con el resto de la cola: antes un solo fallo dejaba
                // sin enviar todas las operaciones siguientes.
                Log.e(TAG, "Excepción en ${operacion.outboxId}: ${e.message}")
                outboxDao.incrementarIntentos(operacion.outboxId)
                huboFalloTransitorio = true
            }
        }

        try {
            revisarConflictosResueltos()
        } catch (e: Exception) {
            Log.d(TAG, "No se pudieron revisar los conflictos: ${e.message}")
        }

        return if (huboFalloTransitorio) Result.retry() else Result.success()
    }

    // ======================================================================
    // FASE 1 — descarga
    // ======================================================================
    private suspend fun descargarCambios() {
        var cursor = personaDao.cursorMaximo()
        var quedanPaginas = true
        var vueltas = 0

        // Se pagina hasta agotar. Con una sola llamada, un dispositivo que
        // estuvo semanas sin señal se quedaría con la primera página y creería
        // estar al día.
        while (quedanPaginas && vueltas < MAX_PAGINAS) {
            val respuesta = personaApi.obtenerCambios(desde = cursor)

            if (!respuesta.isSuccessful) {
                Log.e(TAG, "Descarga rechazada: ${respuesta.code()}")
                return
            }

            val cuerpo = respuesta.body() ?: return
            Log.d(TAG, "Descargados ${cuerpo.personas.size} registros desde $cursor")

            for (dto in cuerpo.personas) {
                val local = personaDao.obtenerPorId(dto.id)

                // Solo se pisa lo local si estaba sincronizado. Un cambio del
                // registrador que todavía no salió del teléfono se respeta:
                // su trabajo no se pierde por una descarga.
                if (local == null || local.syncStatus == SyncStatus.SYNCED.name) {
                    personaDao.guardar(dto.toEntity())
                } else {
                    Log.d(TAG, "Se omite ${dto.id}: tiene cambios locales sin enviar")
                }
            }

            cursor = cuerpo.cursor
            quedanPaginas = cuerpo.hayMas
            vueltas++
        }
    }

    // ======================================================================
    // FASE 2 — envío
    // ======================================================================

    /** Devuelve false cuando conviene cortar la cola entera. */
    private suspend fun enviar(operacion: OutboxEntity): Boolean = when (operacion.operacion) {
        PersonaRepositoryImpl.OPERACION_CREAR -> enviarAlta(operacion)
        PersonaRepositoryImpl.OPERACION_EDITAR -> enviarEdicion(operacion)
        PersonaRepositoryImpl.OPERACION_ELIMINAR -> enviarBaja(operacion)
        else -> {
            Log.e(TAG, "Operación desconocida '${operacion.operacion}', se descarta")
            outboxDao.eliminar(operacion.outboxId)
            true
        }
    }

    private suspend fun enviarAlta(operacion: OutboxEntity): Boolean {
        val alta = gson.fromJson(operacion.payload, AltaPersonaDto::class.java)
        val respuesta = personaApi.crear(alta)

        return when (respuesta.code()) {
            200, 201 -> {
                respuesta.body()?.persona?.let { personaDao.guardar(it.toEntity()) }
                confirmar(operacion)
                true
            }
            409 -> {
                // El documento ya estaba registrado por otra persona. El
                // servidor guardó este intento completo en su tabla de
                // conflictos antes de responder, así que sacarlo de la cola
                // ya no pierde nada: es exactamente lo contrario de lo que
                // hacía el sistema anterior.
                marcarEnRevision(operacion, respuesta)
                true
            }
            400 -> {
                // Datos que la base no acepta. Reintentar da siempre lo mismo.
                Log.e(TAG, "Alta ${operacion.personaId} rechazada por datos inválidos")
                marcarEnRevision(operacion, respuesta)
                true
            }
            else -> continuarOCortar(operacion, respuesta)
        }
    }

    private suspend fun enviarEdicion(operacion: OutboxEntity): Boolean {
        val edicion = gson.fromJson(operacion.payload, EdicionPersonaDto::class.java)
        val respuesta = personaApi.actualizar(operacion.personaId, edicion)

        return when (respuesta.code()) {
            200 -> {
                val cuerpo = respuesta.body()
                cuerpo?.persona?.let { personaDao.guardar(it.toEntity()) }
                if (cuerpo?.mergeado == true) {
                    Log.d(TAG, "${operacion.personaId} se mergeó con un cambio ajeno")
                }
                confirmar(operacion)
                true
            }
            409 -> {
                // Puede ser EDICION_CONCURRENTE — quedó guardado como
                // conflicto — o DOCUMENTO_DUPLICADO / VERSION_INVALIDA, que no
                // se arreglan reintentando. En los tres casos hay que dejar de
                // insistir y avisarle al registrador.
                marcarEnRevision(operacion, respuesta)
                true
            }
            404 -> {
                // La persona ya no existe en el servidor.
                Log.e(TAG, "${operacion.personaId} no existe en el servidor, se borra local")
                personaDao.eliminarFisicamente(operacion.personaId)
                outboxDao.eliminar(operacion.outboxId)
                true
            }
            400 -> {
                marcarEnRevision(operacion, respuesta)
                true
            }
            else -> continuarOCortar(operacion, respuesta)
        }
    }

    private suspend fun enviarBaja(operacion: OutboxEntity): Boolean {
        val baja = gson.fromJson(operacion.payload, BajaPersonaDto::class.java)
        val respuesta = personaApi.eliminar(operacion.personaId, baja)

        return when (respuesta.code()) {
            200, 204 -> {
                personaDao.actualizarSyncStatus(operacion.personaId, SyncStatus.SYNCED.name)
                outboxDao.eliminar(operacion.outboxId)
                true
            }
            404 -> {
                personaDao.eliminarFisicamente(operacion.personaId)
                outboxDao.eliminar(operacion.outboxId)
                true
            }
            409 -> {
                // La persona cambió desde que este teléfono la descargó. La
                // baja no se aplica a ciegas: alguien pudo haber corregido
                // datos justo antes, y borrar encima sería perder ese trabajo.
                marcarEnRevision(operacion, respuesta)
                true
            }
            else -> continuarOCortar(operacion, respuesta)
        }
    }

    // ======================================================================
    private suspend fun confirmar(operacion: OutboxEntity) {
        personaDao.actualizarSyncStatus(operacion.personaId, SyncStatus.SYNCED.name)
        outboxDao.eliminar(operacion.outboxId)
        Log.d(TAG, "Operación ${operacion.outboxId} sincronizada")
    }

    private suspend fun marcarEnRevision(operacion: OutboxEntity, respuesta: Response<*>) {
        val error = leerError(respuesta)
        Log.e(
            TAG,
            "${operacion.personaId} queda en revisión: ${error?.codigo ?: respuesta.code()}"
        )
        personaDao.marcarEnRevision(operacion.personaId, error?.conflictoId)
        outboxDao.eliminar(operacion.outboxId)
    }

    /** Decide si conviene seguir con el resto de la cola o cortar acá. */
    private suspend fun continuarOCortar(operacion: OutboxEntity, respuesta: Response<*>): Boolean {
        return when (respuesta.code()) {
            401 -> {
                // La sesión venció. Nada de lo que sigue va a funcionar, así
                // que se corta y se conserva la cola intacta.
                Log.e(TAG, "Sesión vencida durante la sincronización")
                false
            }
            403 -> {
                // El registrador perdió el permiso sobre este registro, o su
                // cuenta fue desactivada. Reintentar no lo va a devolver.
                Log.e(TAG, "Sin permisos sobre ${operacion.personaId}")
                personaDao.marcarEnRevision(operacion.personaId, null)
                outboxDao.eliminar(operacion.outboxId)
                true
            }
            else -> {
                outboxDao.incrementarIntentos(operacion.outboxId)
                if (operacion.intentos + 1 >= MAX_INTENTOS) {
                    // Se agotaron los reintentos, pero NO se descarta el dato
                    // en silencio: queda visible como pendiente de revisión.
                    Log.e(TAG, "Operación ${operacion.outboxId} agotó los reintentos")
                    personaDao.marcarEnRevision(operacion.personaId, null)
                    outboxDao.eliminar(operacion.outboxId)
                }
                true
            }
        }
    }

    private fun leerError(respuesta: Response<*>): ErrorApiDto? {
        // errorBody() se consume al leerlo, así que se lee una sola vez.
        val cuerpo = respuesta.errorBody()?.string() ?: return null
        return runCatching { gson.fromJson(cuerpo, ErrorApiDto::class.java) }.getOrNull()
    }

    // ======================================================================
    // FASE 3 — qué pasó con lo que quedó en revisión
    // ======================================================================
    private suspend fun revisarConflictosResueltos() {
        val enRevision = personaDao.enRevision()
        if (enRevision.isEmpty()) return

        val respuesta = conflictoApi.mios()
        if (!respuesta.isSuccessful) return

        val resueltos = respuesta.body()?.conflictos.orEmpty()
            .filter { it.estado != ESTADO_PENDIENTE }
            .associateBy { it.id }

        for (persona in enRevision) {
            val conflicto = resueltos[persona.conflictoId ?: continue] ?: continue

            // El admin ya decidió. Qué pasó con este registro depende de cómo
            // lo resolvió, y la única forma confiable de saberlo es preguntar
            // por el id: si fusionó, esta fila ya no existe del lado del
            // servidor; si lo creó como registro nuevo, reusó este mismo id.
            val enServidor = runCatching { personaApi.obtenerPorId(persona.id) }.getOrNull()

            when {
                enServidor?.isSuccessful == true && enServidor.body() != null -> {
                    personaDao.guardar(enServidor.body()!!.toEntity())
                    Log.d(TAG, "${persona.id} resuelto: quedó como registro propio")
                }
                enServidor?.code() == 404 -> {
                    // Se fusionó con otra persona. Esta copia local ya no
                    // corresponde a nada: dejarla mostraría un duplicado que
                    // el servidor no tiene.
                    personaDao.eliminarFisicamente(persona.id)
                    Log.d(TAG, "${persona.id} resuelto: se fusionó con otro registro")
                }
                else -> Log.d(TAG, "No se pudo confirmar el estado de ${persona.id}")
            }

            if (conflicto.estado == ESTADO_DESCARTADO) {
                Log.d(TAG, "El intento sobre ${persona.id} fue descartado por el admin")
            }
        }
    }

    private companion object {
        const val TAG = "SYNC_DEBUG"
        const val MAX_INTENTOS = 4
        const val MAX_PAGINAS = 100
        const val ESTADO_PENDIENTE = "PENDIENTE"
        const val ESTADO_DESCARTADO = "DESCARTADO"
    }
}
