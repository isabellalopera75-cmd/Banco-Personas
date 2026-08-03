package com.tuapp.bancopersonas.presentation.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.bancopersonas.domain.model.Persona

@Composable
fun LoginScreen(
    onLoginSuccess: (String, Persona?) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Registrocc - Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Selector de Rol
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = uiState.rol == "usuario",
                onClick = { viewModel.onRolChange("usuario") }
            )
            Text("Usuario")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(
                selected = uiState.rol == "admin",
                onClick = { viewModel.onRolChange("admin") }
            )
            Text("Admin")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.nombre,
            onValueChange = { viewModel.onNombreChange(it) },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth()
        )

        if (uiState.rol == "admin") {
            OutlinedTextField(
                value = uiState.password,
                onValueChange = { viewModel.onPasswordChange(it) },
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            OutlinedTextField(
                value = uiState.documento,
                onValueChange = { viewModel.onDocumentoChange(it) },
                label = { Text("Documento") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        uiState.error?.let {
            Text(text = it, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.cargando) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { viewModel.login(onLoginSuccess) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ingresar")
            }
        }
    }
}