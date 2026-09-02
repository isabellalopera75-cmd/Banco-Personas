package com.tuapp.bancopersonas.presentation.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.ui.components.CampoTexto
import com.tuapp.bancopersonas.ui.components.MensajeError
import com.tuapp.bancopersonas.ui.components.PantallaConTeclado

@Composable
fun PersonaFormScreen(
    personaExistente: Persona? = null,
    onPersonaGuardada: () -> Unit,
    viewModel: PersonaFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.iniciarFormulario(personaExistente)
    }

    val siguiente = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })

    val guardar = {
        teclado?.hide()
        focusManager.clearFocus()
        viewModel.guardar(onExito = {
            val mensaje = if (uiState.esEdicion) {
                "Cambios guardados"
            } else {
                "¡Listo! Ya podés iniciar sesión"
            }
            android.widget.Toast.makeText(context, mensaje, android.widget.Toast.LENGTH_SHORT).show()
            onPersonaGuardada()
        })
    }

    val puedeGuardar = uiState.nombre.isNotBlank() && uiState.documento.isNotBlank() &&
        (uiState.esEdicion || (uiState.password.isNotBlank() && uiState.confirmacion.isNotBlank()))

    PantallaConTeclado { anchoCompleto ->
        Row(
            modifier = anchoCompleto.padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onPersonaGuardada) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (uiState.esEdicion) "Editar participante" else "Nuevo participante",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Card(
            modifier = anchoCompleto,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CampoTexto(
                    valor = uiState.nombre,
                    onValorCambia = viewModel::actualizarNombre,
                    etiqueta = "Nombre completo",
                    icono = Icons.Default.Person,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = siguiente
                )

                CampoTexto(
                    valor = uiState.documento,
                    onValorCambia = viewModel::actualizarDocumento,
                    etiqueta = "Documento",
                    icono = Icons.Default.Badge,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = siguiente
                )

                CampoTexto(
                    valor = uiState.telefono,
                    onValorCambia = viewModel::actualizarTelefono,
                    etiqueta = "Teléfono",
                    icono = Icons.Default.Phone,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = if (uiState.esEdicion) ImeAction.Done else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        onDone = { if (uiState.esEdicion) guardar() }
                    )
                )

                // Solo al registrar: la edición de datos no toca credenciales.
                if (!uiState.esEdicion) {
                    CampoTexto(
                        valor = uiState.password,
                        onValorCambia = viewModel::actualizarPassword,
                        etiqueta = "Contraseña",
                        icono = Icons.Default.Lock,
                        esPassword = true,
                        textoAyuda = "Mínimo 8 caracteres",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = siguiente
                    )

                    CampoTexto(
                        valor = uiState.confirmacion,
                        onValorCambia = viewModel::actualizarConfirmacion,
                        etiqueta = "Repetir contraseña",
                        icono = Icons.Default.Lock,
                        esPassword = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { guardar() })
                    )
                }

                AnimatedVisibility(visible = uiState.error != null) {
                    MensajeError(
                        mensaje = uiState.error.orEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Button(
            onClick = guardar,
            enabled = puedeGuardar,
            modifier = anchoCompleto
                .padding(top = 16.dp)
                .height(50.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (uiState.esEdicion) "Guardar cambios" else "Crear cuenta",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(modifier = Modifier.size(8.dp))
    }
}
