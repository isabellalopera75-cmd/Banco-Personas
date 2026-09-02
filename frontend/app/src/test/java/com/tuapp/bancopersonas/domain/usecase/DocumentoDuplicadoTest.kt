package com.tuapp.bancopersonas.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Un documento no puede pertenecer a dos participantes activos: lo impide un
 * índice único en la base. Estas pruebas cubren que el choque se detecte antes
 * de encolar nada.
 *
 * El caso que las motivó: al editar el documento de alguien poniéndole uno ya
 * tomado, el servidor respondía 500, la sincronización lo reintentaba cuatro
 * veces y terminaba descartando el cambio sin avisar. Desde la aplicación se
 * veía como "el documento no se actualiza", sin ninguna explicación.
 */
class DocumentoDuplicadoTest {

    private val pasos = mutableListOf<String>()
    private val scheduler = SchedulerFalso(pasos)

    @Test
    fun `editar con un documento libre guarda y sincroniza`() = runBlocking {
        val repository = RepositorioFalso(pasos, ocupadoPor = null)

        val resultado = EditarPersonaUseCase(repository, scheduler)(persona(id = "yo"))

        assertEquals(ResultadoGuardado.Exito, resultado)
        assertEquals(listOf("editarPersona", "sincronizarAhora"), pasos)
    }

    @Test
    fun `editar con el documento de otra persona no guarda nada`() = runBlocking {
        val otra = persona(id = "otra-persona", documento = "1234567")
        val repository = RepositorioFalso(pasos, ocupadoPor = otra)

        val resultado = EditarPersonaUseCase(repository, scheduler)(
            persona(id = "yo", documento = "1234567")
        )

        assertEquals(ResultadoGuardado.DocumentoDuplicado, resultado)
        // Ni se guarda ni se pide sincronización: reintentar contra un índice
        // único da siempre el mismo error.
        assertEquals(emptyList<String>(), pasos)
    }

    @Test
    fun `editar conservando el propio documento si guarda`() = runBlocking {
        // El caso que rompe una comparación ingenua: al cambiar solo el
        // teléfono, la búsqueda por documento se encuentra a sí misma. Hay que
        // comparar por id, no por existencia.
        val yoMismo = persona(id = "yo", documento = "1234567")
        val repository = RepositorioFalso(pasos, ocupadoPor = yoMismo)

        val resultado = EditarPersonaUseCase(repository, scheduler)(yoMismo)

        assertEquals(ResultadoGuardado.Exito, resultado)
        assertEquals(listOf("editarPersona", "sincronizarAhora"), pasos)
    }

    @Test
    fun `el alta con un documento ya tomado no guarda nada`() = runBlocking {
        val otra = persona(id = "otra-persona", documento = "1234567")
        val repository = RepositorioFalso(pasos, ocupadoPor = otra)

        val resultado = CrearPersonaUseCase(repository, scheduler)(
            persona(documento = "1234567"),
            "contrasena-larga"
        )

        assertEquals(ResultadoGuardado.DocumentoDuplicado, resultado)
        assertEquals(emptyList<String>(), pasos)
    }

    @Test
    fun `el alta con un documento libre guarda y sincroniza`() = runBlocking {
        val repository = RepositorioFalso(pasos, ocupadoPor = null)

        val resultado = CrearPersonaUseCase(repository, scheduler)(
            persona(),
            "contrasena-larga"
        )

        assertEquals(ResultadoGuardado.Exito, resultado)
        assertEquals(listOf("crearPersona", "sincronizarAhora"), pasos)
    }
}
