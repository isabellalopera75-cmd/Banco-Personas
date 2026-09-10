package com.tuapp.bancopersonas.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Muestra una fecha como DD/MM/AAAA mientras se escriben solo los dígitos.
 *
 * En el campo se anotan decenas de personas seguidas. Obligar a tipear las
 * barras — o peor, los guiones de un formato ISO que nadie usa al hablar —
 * son ocho pulsaciones extra por persona y una fuente de errores.
 *
 * El valor real que maneja el estado son solo dígitos: "08072008". Las barras
 * existen únicamente en la pantalla.
 */
object FechaVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digitos = text.text.take(8)

        val conBarras = buildString {
            digitos.forEachIndexed { indice, caracter ->
                append(caracter)
                if (indice == 1 || indice == 3) append('/')
            }
        }

        /*
         * El cursor tiene que caer donde la persona espera. Sin este mapeo,
         * al tocar en el medio del texto el cursor salta a otro lugar y
         * corregir un dígito se vuelve una pelea.
         */
        val mapeo = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 1 -> offset
                offset <= 3 -> offset + 1
                offset <= 8 -> offset + 2
                else -> 10
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                offset <= 10 -> offset - 2
                else -> 8
            }
        }

        return TransformedText(AnnotatedString(conBarras), mapeo)
    }
}

/**
 * De los ocho dígitos que se tipean a lo que espera el servidor.
 *
 * El orden es DÍA, MES, AÑO: es como se dice y se escribe una fecha acá, y es
 * lo que el registrador va a copiar del documento que tiene en la mano. El
 * servidor la quiere en ISO, AAAA-MM-DD, así que la conversión ocurre acá y
 * en un solo lugar.
 *
 * Devuelve null si la fecha no existe. Que 31/02 sea rechazado importa: una
 * fecha de nacimiento inventada es un dato malo que después nadie audita.
 */
fun digitosAFechaIso(digitos: String): String? {
    if (digitos.length != 8 || !digitos.all { it.isDigit() }) return null

    val dia = digitos.substring(0, 2).toInt()
    val mes = digitos.substring(2, 4).toInt()
    val anio = digitos.substring(4, 8).toInt()

    if (mes !in 1..12) return null
    if (anio !in 1900..2100) return null

    val diasDelMes = when (mes) {
        4, 6, 9, 11 -> 30
        2 -> if ((anio % 4 == 0 && anio % 100 != 0) || anio % 400 == 0) 29 else 28
        else -> 31
    }
    if (dia !in 1..diasDelMes) return null

    return "%04d-%02d-%02d".format(anio, mes, dia)
}

/** De lo que devuelve el servidor (AAAA-MM-DD) a los ocho dígitos del campo. */
fun fechaIsoADigitos(iso: String?): String {
    val partes = iso.orEmpty().take(10).split("-")
    if (partes.size != 3) return ""
    return partes[2] + partes[1] + partes[0]
}
