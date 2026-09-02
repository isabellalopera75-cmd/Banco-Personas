package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Dobles compartidos por las pruebas de los casos de uso.
 *
 * Están escritos a mano y no con una librería de mocks porque los casos de
 * uso solo dependen de interfaces del dominio. Eso evita arrastrar una
 * dependencia de pruebas nueva para verificar tres llamadas.
 */
internal class RepositorioFalso(
    private val pasos: MutableList<String>,
    private val fallar: Boolean = false,
    /** Lo que devuelve [buscarPorDocumento]. Null = ese documento está libre. */
    private val ocupadoPor: Persona? = null
) : PersonaRepository {

    override fun observarPersonas(): Flow<List<Persona>> = emptyFlow()

    override suspend fun buscarPorDocumento(documento: String): Persona? = ocupadoPor

    override suspend fun crearPersona(persona: Persona, password: String) {
        if (fallar) throw IllegalStateException("no se pudo guardar en el dispositivo")
        pasos += "crearPersona"
    }

    override suspend fun editarPersona(persona: Persona) {
        pasos += "editarPersona"
    }

    override suspend fun eliminarPersona(id: String) {
        pasos += "eliminarPersona"
    }

    override suspend fun sincronizarHistorial(personaId: String) = Unit

    override fun observarHistorial(personaId: String): Flow<List<HistorialEntity>> = emptyFlow()

    override suspend fun revertirHistorial(persona: Persona, historial: HistorialEntity) = Unit

    override suspend fun getGanadorSemana(): Result<Persona> =
        Result.failure(IllegalStateException("sin implementar en la prueba"))
}

internal class SchedulerFalso(private val pasos: MutableList<String>) : SyncScheduler {

    override fun programarSincronizacionPeriodica() {
        pasos += "programarPeriodica"
    }

    override fun sincronizarAhora() {
        pasos += "sincronizarAhora"
    }
}

internal fun persona(
    id: String = "",
    documento: String = "1234567"
) = Persona(
    id = id,
    nombre = "Ana",
    documento = documento,
    telefono = "3001234567",
    syncStatus = SyncStatus.PENDING
)
