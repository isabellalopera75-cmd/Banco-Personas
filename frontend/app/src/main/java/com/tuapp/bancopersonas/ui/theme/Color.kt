package com.tuapp.bancopersonas.ui.theme

import androidx.compose.ui.graphics.Color

// Identidad de marca. El rosa saturado se conserva, pero pasa a usarse solo
// como acento —un borde, un icono activo, el botón principal— y no como
// relleno de superficies grandes ni como halo de sombra: a 16dp de glow
// alrededor de cada tarjeta, ese rosa era lo primero que veía el ojo.
val WinPlayPink = Color(0xFFFF4B72)
val WinPlayPurple = Color(0xFF6B48FF)

/** Rosa apagado para texto y bordes: mismo tono, sin gritar. */
val WinPlayPinkSoft = Color(0xFFFF93A8)

// Fondos. Tres niveles en vez de negro plano: el degradado da profundidad
// sin necesidad de sombras marcadas.
val WinPlayDarkBg = Color(0xFF0F0F13)
val WinPlayDarkBgTop = Color(0xFF191423)
val WinPlaySurface = Color(0xFF1B1B22)
val WinPlaySurfaceAlt = Color(0xFF23232C)

val WinPlayText = Color(0xFFF5F5F7)
val WinPlayTextMuted = Color(0xFF9A9AAB)

/** Borde apenas visible. Reemplaza a las sombras para separar planos. */
val WinPlayOutline = Color(0xFF2E2E3A)

// El error tiene que leerse como una advertencia, no como una alarma: un rojo
// difuminado sobre un fondo tenue del mismo tono, en vez de texto rojo puro
// suelto sobre negro.
val WinPlayError = Color(0xFFFF9DAC)
val WinPlayErrorContainer = Color(0xFF2A1620)
