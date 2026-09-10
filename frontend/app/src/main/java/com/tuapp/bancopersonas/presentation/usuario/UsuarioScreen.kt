package com.tuapp.bancopersonas.presentation.usuario

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.ui.theme.PadronFondo
import com.tuapp.bancopersonas.ui.theme.PadronFondoAlto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuarioScreen(
    onSalir: () -> Unit,
    viewModel: UsuarioViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val persona by viewModel.persona.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Mis datos", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = viewModel::refrescar) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                    IconButton(onClick = { viewModel.cerrarSesion(onSalir) }) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(PadronFondoAlto, PadronFondo)))
                .padding(relleno)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (estado.sinConexion) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Sin conexión. Estos son los datos guardados en el teléfono.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val actual = persona

            if (actual == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (estado.actualizando) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            "No encontramos tus datos en este teléfono.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                return@Column
            }

            Text(
                actual.nombreCompleto,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                actual.documentoCompleto,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))

            FichaDatos(actual)

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Si algún dato está mal, avisale al registrador de tu zona. " +
                    "Desde acá los datos solo se consultan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FichaDatos(persona: Persona) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Dato("Tipo de documento", persona.tipoDocumento.etiqueta)
            Dato("Número de documento", persona.numeroDocumento)
            Dato("Primer nombre", persona.primerNombre)
            persona.segundoNombre?.let { Dato("Segundo nombre", it) }
            Dato("Primer apellido", persona.primerApellido)
            persona.segundoApellido?.let { Dato("Segundo apellido", it) }
            Dato("Fecha de nacimiento", persona.fechaNacimiento)
            Dato("Sexo", persona.sexo.etiqueta)
            Dato("Teléfono", persona.telefono ?: "Sin registrar", ultimo = true)
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, ultimo: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            valor,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (!ultimo) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
