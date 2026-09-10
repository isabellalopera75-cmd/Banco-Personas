package com.tuapp.bancopersonas.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La conversión de fecha decide qué se guarda como fecha de nacimiento.
 *
 * Un error acá no se ve: el registro se guarda igual, con una fecha
 * equivocada, y nadie lo audita hasta que alguien nota que hay recién nacidos
 * de setenta años.
 */
class FechaVisualTest {

    @Test
    fun `el orden es dia mes anio, como se escribe aca`() {
        // 08072008 es el 8 de julio de 2008, no el 7 de agosto.
        assertEquals("2008-07-08", digitosAFechaIso("08072008"))
    }

    @Test
    fun `convierte una fecha corriente`() {
        assertEquals("2000-12-31", digitosAFechaIso("31122000"))
        assertEquals("1958-03-12", digitosAFechaIso("12031958"))
    }

    @Test
    fun `rellena con ceros a la izquierda`() {
        assertEquals("1900-01-01", digitosAFechaIso("01011900"))
    }

    @Test
    fun `acepta el 29 de febrero de un anio bisiesto`() {
        assertEquals("2024-02-29", digitosAFechaIso("29022024"))
        // 2000 es bisiesto: divisible por 400.
        assertEquals("2000-02-29", digitosAFechaIso("29022000"))
    }

    @Test
    fun `rechaza el 29 de febrero de un anio que no es bisiesto`() {
        assertNull(digitosAFechaIso("29022023"))
        // 1900 NO es bisiesto: divisible por 100 pero no por 400.
        assertNull(digitosAFechaIso("29021900"))
    }

    @Test
    fun `rechaza dias que ese mes no tiene`() {
        assertNull("31 de febrero", digitosAFechaIso("31022008"))
        assertNull("31 de abril", digitosAFechaIso("31042008"))
        assertNull("31 de junio", digitosAFechaIso("31062008"))
        assertNull("31 de noviembre", digitosAFechaIso("31112008"))
    }

    @Test
    fun `rechaza dia o mes fuera de rango`() {
        assertNull("día cero", digitosAFechaIso("00072008"))
        assertNull("mes cero", digitosAFechaIso("08002008"))
        assertNull("mes trece", digitosAFechaIso("08132008"))
    }

    @Test
    fun `rechaza anios imposibles para una persona viva`() {
        assertNull(digitosAFechaIso("08071899"))
        assertNull(digitosAFechaIso("08072101"))
    }

    @Test
    fun `rechaza lo que no sean ocho digitos`() {
        assertNull("faltan dígitos", digitosAFechaIso("0807200"))
        assertNull("sobran dígitos", digitosAFechaIso("080720081"))
        assertNull("vacío", digitosAFechaIso(""))
        assertNull("con letras", digitosAFechaIso("0807200a"))
        assertNull("con barras", digitosAFechaIso("08/07/20"))
    }

    @Test
    fun `de vuelta desde lo que manda el servidor`() {
        assertEquals("08072008", fechaIsoADigitos("2008-07-08"))
        // El servidor a veces incluye la hora; solo importan los diez primeros.
        assertEquals("12031958", fechaIsoADigitos("1958-03-12T00:00:00.000Z"))
    }

    @Test
    fun `una fecha vacia del servidor no rompe la pantalla`() {
        assertEquals("", fechaIsoADigitos(null))
        assertEquals("", fechaIsoADigitos(""))
        assertEquals("", fechaIsoADigitos("no es una fecha"))
    }

    @Test
    fun `ida y vuelta conserva el valor`() {
        for (digitos in listOf("08072008", "29022024", "31121999", "01012100")) {
            val iso = digitosAFechaIso(digitos)
            assertEquals(digitos, fechaIsoADigitos(iso))
        }
    }
}

/**
 * La transformación visual, probada sin dispositivo.
 *
 * El mapeo de posiciones es lo que decide dónde cae el cursor. Si está mal,
 * tocar en el medio de la fecha para corregir un dígito manda el cursor a
 * otro lado, y el registrador pelea con el campo en vez de escribir.
 */
class FechaVisualTransformationTest {

    private fun mostrado(digitos: String) =
        FechaVisualTransformation
            .filter(androidx.compose.ui.text.AnnotatedString(digitos))
            .text.text

    @Test
    fun `las barras aparecen en las posiciones correctas`() {
        assertEquals("08/07/2008", mostrado("08072008"))
    }

    @Test
    fun `las barras van apareciendo mientras se escribe`() {
        assertEquals("", mostrado(""))
        assertEquals("0", mostrado("0"))
        assertEquals("08/", mostrado("08"))
        assertEquals("08/0", mostrado("080"))
        assertEquals("08/07/", mostrado("0807"))
        assertEquals("08/07/2", mostrado("08072"))
        assertEquals("08/07/2008", mostrado("08072008"))
    }

    @Test
    fun `ignora lo que sobre de ocho digitos`() {
        assertEquals("08/07/2008", mostrado("0807200899"))
    }

    @Test
    fun `el cursor cae donde corresponde en el texto con barras`() {
        val mapeo = FechaVisualTransformation
            .filter(androidx.compose.ui.text.AnnotatedString("08072008"))
            .offsetMapping

        // Antes de la primera barra las posiciones coinciden.
        assertEquals(0, mapeo.originalToTransformed(0))
        assertEquals(1, mapeo.originalToTransformed(1))

        // Con dos dígitos escritos el cursor queda DESPUÉS de la barra, no
        // antes: si quedara antes, el tercer dígito se escribiría del lado
        // equivocado del separador.
        assertEquals(3, mapeo.originalToTransformed(2))
        assertEquals(4, mapeo.originalToTransformed(3))

        // Pasada la segunda barra, dos caracteres de diferencia.
        assertEquals(6, mapeo.originalToTransformed(4))
        assertEquals(10, mapeo.originalToTransformed(8))
    }

    @Test
    fun `el mapeo inverso devuelve la posicion real`() {
        val mapeo = FechaVisualTransformation
            .filter(androidx.compose.ui.text.AnnotatedString("08072008"))
            .offsetMapping

        // Nunca puede devolver una posición fuera de los ocho dígitos: si lo
        // hiciera, Compose lanza IndexOutOfBounds y la pantalla se cae.
        for (posicion in 0..10) {
            val real = mapeo.transformedToOriginal(posicion)
            assertEquals("posición $posicion fuera de rango", true, real in 0..8)
        }
        assertEquals(0, mapeo.transformedToOriginal(0))
        assertEquals(8, mapeo.transformedToOriginal(10))
    }
}
