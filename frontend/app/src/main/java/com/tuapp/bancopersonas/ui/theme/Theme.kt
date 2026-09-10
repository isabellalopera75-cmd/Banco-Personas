package com.tuapp.bancopersonas.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EsquemaClaro = lightColorScheme(
    primary = PadronPrimario,
    onPrimary = Color.White,
    primaryContainer = PadronPrimarioSuave,
    onPrimaryContainer = PadronPrimarioOscuro,

    secondary = PadronPrimarioOscuro,
    onSecondary = Color.White,
    tertiary = PadronPrimario,

    background = PadronFondo,
    onBackground = PadronTinta,

    surface = PadronSuperficie,
    onSurface = PadronTinta,
    surfaceVariant = PadronSuperficieAlt,
    onSurfaceVariant = PadronTintaSuave,

    outline = PadronBorde,
    outlineVariant = PadronBorde,

    // El error se muestra como un bloque tenue y legible, no como un renglón
    // rojo suelto. Un aviso que grita se ignora igual que uno que no se ve.
    error = PadronError,
    onError = Color.White,
    errorContainer = PadronErrorContenedor,
    onErrorContainer = PadronSobreErrorContenedor
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
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Siempre claro, y no por gusto: esta aplicación se usa al sol. Un tema
    // oscuro es más difícil de leer al aire libre, y seguir el tema del
    // sistema dejaría esa decisión librada a cómo cada quien configuró su
    // teléfono. El color dinámico está apagado por lo mismo: volvería la
    // aplicación otra en cada dispositivo.
    MaterialTheme(
        colorScheme = EsquemaClaro,
        typography = Typography,
        shapes = Formas,
        content = content
    )
}
