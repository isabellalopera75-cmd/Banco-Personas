package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Toda operación que se encola sin conexión tiene que pedir una subida
 * inmediata, no esperar a la ventana periódica.
 *
 * El caso que motivó estas pruebas es el alta: quien se registraba quedaba
 * guardado solo en el teléfono, volvía al login, y el servidor —que todavía
 * no conocía su documento— respondía 401. Con conexión y todo, no había
 * forma de entrar.
 */
class SincronizacionInmediataTest {

    private val pasos = mutableListOf<String>()
    private val repository = RepositorioFalso(pasos)
    private val scheduler = SchedulerFalso(pasos)

    private val persona = Persona(
        id = "",
        nombre = "Ana",
        documento = "1234567",
        telefono = "3001234567",
        syncStatus = SyncStatus.PENDING
    )

    @Test
    fun `el alta pide la sincronizacion despues de guardar en el dispositivo`() = runBlocking {
        CrearPersonaUseCase(repository, scheduler)(persona, "contrasena-larga")

        // El orden importa: si se pidiera la sincronización antes de guardar,
        // el worker podría leer la cola cuando la operación todavía no está.
        assertEquals(listOf("crearPersona", "sincronizarAhora"), pasos)
    }

    @Test
    fun `si el alta local falla no se pide sincronizacion`() {
        val repositorioQueFalla = RepositorioFalso(pasos, fallar = true)

        runCatching {
            runBlocking { CrearPersonaUseCase(repositorioQueFalla, scheduler)(persona, "x") }
        }

        // No hay nada que subir: pedir una corrida solo gastaría batería.
        assertEquals(emptyList<String>(), pasos)
    }

    @Test
    fun `la edicion pide la sincronizacion despues de guardar`() = runBlocking {
        EditarPersonaUseCase(repository, scheduler)(persona)

        assertEquals(listOf("editarPersona", "sincronizarAhora"), pasos)
    }

    @Test
    fun `la baja pide la sincronizacion despues de guardar`() = runBlocking {
        EliminarPersonaUseCase(repository, scheduler)("id-1")

        assertEquals(listOf("eliminarPersona", "sincronizarAhora"), pasos)
    }
}

private class RepositorioFalso(
    private val pasos: MutableList<String>,
    private val fallar: Boolean = false
) : PersonaRepository {

    override fun observarPersonas(): Flow<List<Persona>> = emptyFlow()

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

private class SchedulerFalso(private val pasos: MutableList<String>) : SyncScheduler {

    override fun programarSincronizacionPeriodica() {
        pasos += "programarPeriodica"
    }

    override fun sincronizarAhora() {
        pasos += "sincronizarAhora"
    }
}
