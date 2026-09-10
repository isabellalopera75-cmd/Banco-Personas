package com.tuapp.bancopersonas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tuapp.bancopersonas.data.local.SessionManager
import com.tuapp.bancopersonas.presentation.conflictos.ConflictosScreen
import com.tuapp.bancopersonas.presentation.form.PersonaFormScreen
import com.tuapp.bancopersonas.presentation.list.PersonaListScreen
import com.tuapp.bancopersonas.presentation.login.LoginScreen
import com.tuapp.bancopersonas.presentation.usuario.UsuarioScreen
import com.tuapp.bancopersonas.ui.theme.RegistroccTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Pantallas de la aplicación.
 *
 * El alta pública desapareció: ya no hay una pantalla de registro a la que se
 * llegue sin sesión. A las personas las da de alta un registrador autenticado.
 */
sealed interface Pantalla {
    data object Login : Pantalla

    /** Padrón. El registrador ve lo suyo; el admin, todo. */
    data object Padron : Pantalla

    /** Alta si [personaId] es null, edición si no. */
    data class Formulario(val personaId: String?) : Pantalla

    data object Conflictos : Pantalla

    /** Rol usuario: sus propios datos, de solo lectura. */
    data object MisDatos : Pantalla
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RegistroccTheme {
                Navegacion(sessionManager)
            }
        }
    }
}

@Composable
private fun Navegacion(sessionManager: SessionManager) {
    // Se arranca donde corresponda según la sesión guardada. Un registrador
    // que cerró la aplicación en el campo no tiene por qué volver a escribir
    // su contraseña, sobre todo cuando puede no haber señal para validarla.
    var pantalla by remember {
        mutableStateOf(
            when {
                !sessionManager.haySesion -> Pantalla.Login
                sessionManager.rol == SessionManager.ROL_USUARIO -> Pantalla.MisDatos
                else -> Pantalla.Padron
            }
        )
    }

    // El botón físico de volver tiene que salir de las pantallas secundarias,
    // no de la aplicación entera con datos a medio cargar.
    BackHandler(enabled = pantalla is Pantalla.Formulario || pantalla is Pantalla.Conflictos) {
        pantalla = Pantalla.Padron
    }

    when (val actual = pantalla) {
        is Pantalla.Login -> LoginScreen(
            onEntrarComoOperador = { _, _ -> pantalla = Pantalla.Padron },
            onEntrarComoPersona = { _, _ -> pantalla = Pantalla.MisDatos },
        )

        is Pantalla.Padron -> PersonaListScreen(
            onNuevaPersona = { pantalla = Pantalla.Formulario(null) },
            onEditarPersona = { id -> pantalla = Pantalla.Formulario(id) },
            onVerConflictos = { pantalla = Pantalla.Conflictos },
            onSalir = { pantalla = Pantalla.Login },
        )

        is Pantalla.Formulario -> PersonaFormScreen(
            personaId = actual.personaId,
            onGuardada = { pantalla = Pantalla.Padron },
            onCancelar = { pantalla = Pantalla.Padron },
        )

        is Pantalla.Conflictos -> ConflictosScreen(
            onVolver = { pantalla = Pantalla.Padron },
        )

        is Pantalla.MisDatos -> UsuarioScreen(
            onSalir = { pantalla = Pantalla.Login },
        )
    }
}
