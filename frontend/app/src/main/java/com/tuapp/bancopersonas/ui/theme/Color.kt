package com.tuapp.bancopersonas.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Paleta del padrón.
 *
 * Reemplaza a la anterior, heredada de la aplicación de sorteos: rosa neón y
 * púrpura sobre negro. Esa combinación decía "casino", y esto es un registro
 * de salud.
 *
 * El cambio de fondo oscuro a claro no es solo estético. Quien usa esta
 * aplicación trabaja al aire libre: un registrador anotando personas en una
 * vereda, con el teléfono al sol. Texto gris apagado sobre negro es casi
 * ilegible en esa condición, y esa condición es la normal acá, no la
 * excepción.
 *
 * Los contrastes cumplen WCAG AA sobre el fondo claro:
 * Tinta 15.8:1, TintaSuave 5.4:1, Primario 5.6:1.
 */

/** Verde azulado. Lee cuidado y salud sin caer en el azul corporativo. */
val PadronPrimario = Color(0xFF0F766E)
val PadronPrimarioOscuro = Color(0xFF115E59)

/** Fondo teñido para chips y bloques de acento. */
val PadronPrimarioSuave = Color(0xFFE6F4F1)
val PadronPrimarioLinea = Color(0xFF99D6CD)

val PadronTinta = Color(0xFF0F1E23)
val PadronTintaSuave = Color(0xFF52646B)

val PadronFondo = Color(0xFFF5F8F8)

/** Extremo superior del degradado de pantalla. Apenas se despega del fondo. */
val PadronFondoAlto = Color(0xFFFFFFFF)

val PadronSuperficie = Color(0xFFFFFFFF)
val PadronSuperficieAlt = Color(0xFFEEF3F3)

/** Borde apenas visible. Separa planos sin recurrir a sombras. */
val PadronBorde = Color(0xFFDDE6E7)

/*
 * Los tres estados de sincronización tienen colores distintos y deliberados.
 *
 * EN_REVISION usa el tono de error, pero eso no significa que algo salió mal:
 * significa que hace falta una decisión humana. El texto que lo acompaña lo
 * aclara — la etiqueta dice "En revisión del administrador", nunca "error".
 */
val PadronExito = Color(0xFF15803D)
val PadronAdvertencia = Color(0xFFB45309)
val PadronError = Color(0xFFB3261E)

val PadronErrorContenedor = Color(0xFFFCEDEC)
val PadronSobreErrorContenedor = Color(0xFF7F1D1B)
