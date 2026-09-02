package com.tuapp.bancopersonas.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Adaptador del puerto [SyncScheduler] sobre WorkManager. */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SyncScheduler {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    // Los dos trabajos esperan a que haya red. Sin la restricción, una corrida
    // sin conexión se gasta el intento y deja la cola igual que antes.
    private val requiereRed = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun programarSincronizacionPeriodica() {
        val periodico = PeriodicWorkRequestBuilder<SyncWorker>(
            INTERVALO_RESPALDO_MINUTOS, TimeUnit.MINUTES
        )
            .setConstraints(requiereRed)
            .build()

        workManager.enqueueUniquePeriodicWork(
            TRABAJO_PERIODICO,
            ExistingPeriodicWorkPolicy.KEEP,
            periodico
        )
    }

    override fun sincronizarAhora() {
        val inmediato = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(requiereRed)
            .build()

        workManager.enqueueUniqueWork(
            // Nombre propio, distinto al del periódico: WorkManager comparte
            // el espacio de nombres entre trabajos únicos, y reusarlo acá
            // cancelaría la sincronización de respaldo en cada operación.
            TRABAJO_INMEDIATO,
            // APPEND_OR_REPLACE y no KEEP: si ya hay una corrida en marcha,
            // esa corrida leyó la cola antes de este encolado y no vería la
            // operación recién agregada. KEEP la descartaría en silencio.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            inmediato
        )
    }

    private companion object {
        const val TRABAJO_PERIODICO = "sync_personas"
        const val TRABAJO_INMEDIATO = "sync_personas_inmediato"
        const val INTERVALO_RESPALDO_MINUTOS = 15L
    }
}
