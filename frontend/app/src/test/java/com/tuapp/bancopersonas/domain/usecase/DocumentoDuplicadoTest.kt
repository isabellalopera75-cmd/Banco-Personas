package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.model.TipoDocumento
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un documento no puede pertenecer a dos personas activas: lo impide un índice
 * único en la base. Estas pruebas cubren que el choque se detecte ANTES de
 * encolar nada.
 *
 * El caso que las motivó: al editar el documento de alguien poniéndole uno ya
 * tomado, el servidor respondía 500, la sincronización lo reintentaba cuatro
 * veces y terminaba descartando el cambio sin avisar. Desde la aplicación se
 * veía como "el documento no se actualiza", sin ninguna explicación.
 *
 * Esta comprobación local NO reemplaza a la del servidor: otro registrador
 * puede estar dando de alta a la misma persona en este mismo momento, sin
 * señal. Ese caso lo resuelve la cola de conflictos.
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
        val otra = persona(id = "otra-persona", numeroDocumento = "1234567", primerNombre = "Rosa")
        val repository = RepositorioFalso(pasos, ocupadoPor = otra)

        val resultado = EditarPersonaUseCase(repository, scheduler)(
            persona(id = "yo", numeroDocumento = "1234567")
        )

        assertTrue(resultado is ResultadoGuardado.DocumentoDuplicado)
        // El mensaje nombra a quién pertenece: sin eso, el registrador no sabe
        // si se equivocó de dígito o si ya la había registrado antes.
        assertTrue(
            (resultado as ResultadoGuardado.DocumentoDuplicado).nombreExistente.contains("Rosa")
        )
        // Ni se guarda ni se pide sincronización: reintentar contra un índice
        // único da siempre el mismo error.
        assertEquals(emptyList<String>(), pasos)
    }

    @Test
    fun `editar conservando el propio documento si guarda`() = runBlocking {
        // El caso que rompe una comparación ingenua: al cambiar solo el
        // teléfono, la búsqueda por documento se encuentra a sí misma. Hay que
        // comparar por id, no por existencia.
        val yoMismo = persona(id = "yo", numeroDocumento = "1234567")
        val repository = RepositorioFalso(pasos, ocupadoPor = yoMismo)

        val resultado = EditarPersonaUseCase(repository, scheduler)(yoMismo)

        assertEquals(ResultadoGuardado.Exito, resultado)
        assertEquals(listOf("editarPersona", "sincronizarAhora"), pasos)
    }

    @Test
    fun `el alta con un documento ya tomado no guarda nada`() = runBlocking {
        val otra = persona(id = "otra-persona", numeroDocumento = "1234567")
        val repository = RepositorioFalso(pasos, ocupadoPor = otra)

        val resultado = CrearPersonaUseCase(repository, scheduler)(
            persona(numeroDocumento = "1234567")
        )

        assertTrue(resultado is ResultadoGuardado.DocumentoDuplicado)
        assertEquals(emptyList<String>(), pasos)
    }

    @Test
    fun `el alta con un documento libre guarda y sincroniza`() = runBlocking {
        val repository = RepositorioFalso(pasos, ocupadoPor = null)

        val resultado = CrearPersonaUseCase(repository, scheduler)(persona())

        assertEquals(ResultadoGuardado.Exito, resultado)
        assertEquals(listOf("crearPersona", "sincronizarAhora"), pasos)
    }

    @Test
    fun `un mismo numero con distinto tipo de documento no es duplicado`() = runBlocking {
        // Una CC y una TI pueden compartir número: son personas distintas. El
        // esquema anterior indexaba solo el número y rechazaba altas legítimas.
        // Acá se comprueba que la búsqueda local viaje con el PAR completo.
        val repository = RepositorioFalso(pasos, ocupadoPor = null)

        val resultado = CrearPersonaUseCase(repository, scheduler)(
            persona(tipoDocumento = TipoDocumento.TI, numeroDocumento = "1234567")
        )

        assertEquals(ResultadoGuardado.Exito, resultado)
    }

    @Test
    fun `un alta sin los campos obligatorios no llega al repositorio`() = runBlocking {
        val repository = RepositorioFalso(pasos, ocupadoPor = null)

        val resultado = CrearPersonaUseCase(repository, scheduler)(
            persona(primerApellido = "", fechaNacimiento = "")
        )

        assertTrue(resultado is ResultadoGuardado.Incompleto)
        assertEquals(
            listOf("Primer apellido", "Fecha de nacimiento"),
            (resultado as ResultadoGuardado.Incompleto).campos
        )
        assertEquals(emptyList<String>(), pasos)
    }
}
