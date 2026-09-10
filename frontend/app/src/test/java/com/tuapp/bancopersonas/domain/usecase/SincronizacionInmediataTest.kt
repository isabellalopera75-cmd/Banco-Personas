package com.tuapp.bancopersonas.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Toda operación que se encola sin conexión tiene que pedir una subida
 * inmediata, no esperar a la ventana periódica.
 *
 * El caso que motivó estas pruebas es el alta: el registro quedaba guardado
 * solo en el teléfono y podía tardar minutos en subir, aunque hubiera señal.
 * En campo eso significa que el registrador se va creyendo que ya está.
 */
class SincronizacionInmediataTest {

    private val pasos = mutableListOf<String>()
    private val repository = RepositorioFalso(pasos)
    private val scheduler = SchedulerFalso(pasos)

    @Test
    fun `el alta pide la sincronizacion despues de guardar en el dispositivo`() = runBlocking {
        CrearPersonaUseCase(repository, scheduler)(persona())

        // El orden importa: si se pidiera la sincronización antes de guardar,
        // el worker podría leer la cola cuando la operación todavía no está.
        assertEquals(listOf("crearPersona", "sincronizarAhora"), pasos)
    }

    @Test
    fun `si el alta local falla no se pide sincronizacion`() {
        val repositorioQueFalla = RepositorioFalso(pasos, fallar = true)

        runCatching {
            runBlocking { CrearPersonaUseCase(repositorioQueFalla, scheduler)(persona()) }
        }

        // No hay nada que subir: pedir una corrida solo gastaría batería.
        assertEquals(emptyList<String>(), pasos)
    }

    @Test
    fun `la edicion pide la sincronizacion despues de guardar`() = runBlocking {
        EditarPersonaUseCase(repository, scheduler)(persona(id = "yo"))

        assertEquals(listOf("editarPersona", "sincronizarAhora"), pasos)
    }

    @Test
    fun `la baja pide la sincronizacion despues de guardar`() = runBlocking {
        EliminarPersonaUseCase(repository, scheduler)("id-1")

        assertEquals(listOf("eliminarPersona", "sincronizarAhora"), pasos)
    }
}
