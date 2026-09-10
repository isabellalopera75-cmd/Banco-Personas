package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.Sexo
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.model.TipoDocumento
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Dobles compartidos por las pruebas de los casos de uso.
 *
 * Están escritos a mano y no con una librería de mocks porque los casos de uso
 * solo dependen de interfaces del dominio. Eso evita arrastrar una dependencia
 * de pruebas nueva para verificar tres llamadas.
 */
internal class RepositorioFalso(
    private val pasos: MutableList<String>,
    private val fallar: Boolean = false,
    /** Lo que devuelve [buscarPorDocumento]. Null = ese documento está libre. */
    private val ocupadoPor: Persona? = null,
    private val pendientes: Int = 0,
) : PersonaRepository {

    override fun observarPersonas(): Flow<List<Persona>> = emptyFlow()

    override fun observarPersona(id: String): Flow<Persona?> = flowOf(null)

    override suspend fun obtenerPorId(id: String): Persona? = ocupadoPor

    override suspend fun buscarPorDocumento(tipo: TipoDocumento, numero: String): Persona? =
        ocupadoPor

    override suspend fun crearPersona(persona: Persona) {
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

    override suspend fun revertirCambio(persona: Persona, historial: HistorialEntity) = Unit

    override suspend fun cambiosSinEnviar(): Int = pendientes

    override suspend fun limpiarDatosLocales(): Result<Unit> {
        if (pendientes > 0) {
            return Result.failure(IllegalStateException("Quedan $pendientes cambios sin enviar."))
        }
        pasos += "limpiarDatosLocales"
        return Result.success(Unit)
    }
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
    id: String = "id-por-defecto",
    tipoDocumento: TipoDocumento = TipoDocumento.CC,
    numeroDocumento: String = "1234567",
    primerNombre: String = "Ana",
    primerApellido: String = "Gomez",
    fechaNacimiento: String = "1990-04-12",
) = Persona(
    id = id,
    tipoDocumento = tipoDocumento,
    numeroDocumento = numeroDocumento,
    primerNombre = primerNombre,
    primerApellido = primerApellido,
    fechaNacimiento = fechaNacimiento,
    sexo = Sexo.F,
    telefono = "3001234567",
    syncStatus = SyncStatus.PENDING,
)
