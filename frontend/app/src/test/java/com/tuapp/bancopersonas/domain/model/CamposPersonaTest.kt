package com.tuapp.bancopersonas.domain.model

import com.tuapp.bancopersonas.data.local.entity.PersonaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cálculo de diferencias es la contraparte del merge del servidor.
 *
 * Si acá se cuela un campo que en realidad no cambió, el servidor lo va a ver
 * como tocado y va a levantar un conflicto donde nadie modificó nada. Y si se
 * pierde un campo que sí cambió, ese dato no se guarda nunca y la pantalla lo
 * muestra igual, porque la fila local ya tiene el valor nuevo.
 *
 * Los dos errores son silenciosos. Por eso esta lógica va aparte y con tests.
 */
class CamposPersonaTest {

    private val guardada = PersonaEntity(
        id = "11111111-1111-4111-8111-111111111111",
        tipoDocumento = "CC",
        numeroDocumento = "1234",
        primerNombre = "Rosa",
        segundoNombre = null,
        primerApellido = "Perez",
        segundoApellido = null,
        fechaNacimiento = "1960-05-01",
        sexo = "F",
        telefono = null,
        creadoPor = "22222222-2222-4222-8222-222222222222",
        version = 3,
        syncStatus = "SYNCED",
    )

    private fun comoDominio(
        telefono: String? = null,
        primerNombre: String = "Rosa",
        segundoNombre: String? = null,
        numeroDocumento: String = "1234",
        tipoDocumento: TipoDocumento = TipoDocumento.CC,
    ) = Persona(
        id = guardada.id,
        tipoDocumento = tipoDocumento,
        numeroDocumento = numeroDocumento,
        primerNombre = primerNombre,
        segundoNombre = segundoNombre,
        primerApellido = "Perez",
        segundoApellido = null,
        fechaNacimiento = "1960-05-01",
        sexo = Sexo.F,
        telefono = telefono,
        version = 3,
        syncStatus = SyncStatus.PENDING,
    )

    @Test
    fun `detecta un campo que cambio`() {
        val cambios = CamposPersona.diferencias(guardada, comoDominio(telefono = "3001112233"))

        assertEquals(mapOf(CamposPersona.TELEFONO to "3001112233"), cambios)
    }

    @Test
    fun `ignora un campo enviado con el mismo valor`() {
        // El formulario devuelve el registro entero aunque se haya tocado un
        // solo campo. Sin este filtro, cada edición mandaría los nueve campos
        // y cualquier cambio simultáneo sería un choque.
        val cambios = CamposPersona.diferencias(
            guardada,
            comoDominio(telefono = "3001112233", primerNombre = "Rosa")
        )

        assertEquals(setOf(CamposPersona.TELEFONO), cambios.keys)
    }

    @Test
    fun `un formulario sin tocar no produce ningun cambio`() {
        assertTrue(CamposPersona.diferencias(guardada, comoDominio()).isEmpty())
    }

    @Test
    fun `nulo y cadena vacia son el mismo valor`() {
        // Un opcional en blanco significa "sin dato", no "el texto vacío". Sin
        // unificarlos, borrar un teléfono viajaría como null -> "" y después
        // "" -> null: ruido permanente en la auditoría.
        val cambios = CamposPersona.diferencias(guardada, comoDominio(segundoNombre = "   "))

        assertTrue(cambios.isEmpty())
    }

    @Test
    fun `los espacios de mas no cuentan como cambio`() {
        val cambios = CamposPersona.diferencias(guardada, comoDominio(primerNombre = "  Rosa  "))

        assertTrue(cambios.isEmpty())
    }

    @Test
    fun `el valor enviado va recortado`() {
        val cambios = CamposPersona.diferencias(guardada, comoDominio(primerNombre = "  Rosalia  "))

        assertEquals("Rosalia", cambios[CamposPersona.PRIMER_NOMBRE])
    }

    @Test
    fun `borrar un opcional viaja como nulo, no como cadena vacia`() {
        val conTelefono = guardada.copy(telefono = "3001112233")

        val cambios = CamposPersona.diferencias(conTelefono, comoDominio(telefono = ""))

        assertTrue(cambios.containsKey(CamposPersona.TELEFONO))
        assertEquals(null, cambios[CamposPersona.TELEFONO])
    }

    @Test
    fun `informa varios cambios a la vez`() {
        val cambios = CamposPersona.diferencias(
            guardada,
            comoDominio(telefono = "3001112233", primerNombre = "Rosalia")
        )

        assertEquals(
            setOf(CamposPersona.TELEFONO, CamposPersona.PRIMER_NOMBRE),
            cambios.keys
        )
    }

    @Test
    fun `el tipo de documento viaja como codigo y no como etiqueta`() {
        val cambios = CamposPersona.diferencias(
            guardada,
            comoDominio(tipoDocumento = TipoDocumento.TI)
        )

        assertEquals("TI", cambios[CamposPersona.TIPO_DOCUMENTO])
    }

    @Test
    fun `las claves son las que espera el servidor`() {
        // Un typo acá NO rompe la compilación: el servidor ignora ese campo y
        // lo devuelve en `rechazados`. El cambio parecería guardarse sin
        // guardarse, y la pantalla mostraría el valor nuevo igual porque la
        // fila local ya lo tiene.
        val cambios = CamposPersona.diferencias(
            guardada,
            comoDominio(telefono = "3001112233", numeroDocumento = "9999")
        )

        assertEquals(setOf("telefono", "numero_documento"), cambios.keys)
    }
}
