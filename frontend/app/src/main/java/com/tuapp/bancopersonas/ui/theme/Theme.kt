package com.tuapp.bancopersonas.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EsquemaOscuro = darkColorScheme(
    primary = WinPlayPink,
    onPrimary = Color.White,
    secondary = WinPlayPurple,
    onSecondary = Color.White,
    tertiary = WinPlayPurple,

    background = WinPlayDarkBg,
    onBackground = WinPlayText,

    surface = WinPlaySurface,
    onSurface = WinPlayText,
    surfaceVariant = WinPlaySurfaceAlt,
    onSurfaceVariant = WinPlayTextMuted,

    outline = WinPlayOutline,
    outlineVariant = WinPlayOutline,

    // Antes el error caía en el rojo por defecto de Material. Con estos dos
    // el mensaje se muestra como un bloque tenue y legible en lugar de un
    // renglón rojo suelto.
    error = WinPlayError,
    onError = Color(0xFF3A0A14),
    errorContainer = WinPlayErrorContainer,
    onErrorContainer = WinPlayError
)

// Esquinas generosas y parejas. Las formas hacen más por la sensación de
// suavidad que cualquier sombra, y no cuestan nada de rendimiento.
private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RegistroccTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // La aplicación es siempre oscura a propósito: es su identidad, y el color
    // dinámico del sistema la volvería otra cosa en cada teléfono.
    MaterialTheme(
        colorScheme = EsquemaOscuro,
        typography = Typography,
        shapes = Formas,
        content = content
    )
}
