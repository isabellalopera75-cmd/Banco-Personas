package com.tuapp.bancopersonas.presentation.usuario

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tuapp.bancopersonas.domain.model.Persona

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuarioScreen(
    persona: Persona,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Ficha Personal") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Cerrar Sesión")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DatoFicha(label = "Nombre", valor = persona.nombre)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DatoFicha(label = "Documento", valor = persona.documento)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    DatoFicha(label = "Teléfono", valor = persona.telefono)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Estado de sincronización: ${persona.syncStatus}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun DatoFicha(label: String, valor: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = valor, style = MaterialTheme.typography.bodyLarge)
    }
}