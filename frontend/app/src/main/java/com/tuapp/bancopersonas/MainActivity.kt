package com.tuapp.bancopersonas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.presentation.list.PersonaListScreen
import com.tuapp.bancopersonas.presentation.login.LoginScreen
import com.tuapp.bancopersonas.presentation.usuario.UsuarioScreen
import com.tuapp.bancopersonas.ui.theme.RegistroccTheme
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen {
    object Login : Screen()
    object Admin : Screen()
    data class Usuario(val persona: Persona) : Screen()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RegistroccTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (val screen = currentScreen) {
                            is Screen.Login -> {
                                LoginScreen(
                                    onLoginSuccess = { rol, persona ->
                                        currentScreen = if (rol == "admin") {
                                            Screen.Admin
                                        } else {
                                            Screen.Usuario(persona!!)
                                        }
                                    }
                                )
                            }
                            is Screen.Admin -> {
                                PersonaListScreen(
                                    onLogout = { currentScreen = Screen.Login }
                                )
                            }
                            is Screen.Usuario -> {
                                UsuarioScreen(
                                    persona = screen.persona,
                                    onLogout = { currentScreen = Screen.Login }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}