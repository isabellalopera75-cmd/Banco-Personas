package com.tuapp.bancopersonas.presentation.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
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
fun LoginScreen(
    onLoginSuccess: (String, Persona?) -> Unit,
    onRegisterClick: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current

    val esAdmin = uiState.rol == "admin"

    val ingresar = {
        // Se baja el teclado antes de enviar. Si no, tapa el mensaje de error
        // que aparece justo debajo del formulario.
        teclado?.hide()
        focusManager.clearFocus()
        viewModel.login(onLoginSuccess)
    }

    PantallaConTeclado { anchoCompleto ->
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "WinPlay",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "Iniciá sesión para participar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
        )

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
                TabRow(
                    selectedTabIndex = if (esAdmin) 1 else 0,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                ) {
                    listOf("Participante" to "usuario", "Administrador" to "admin")
                        .forEachIndexed { indice, (titulo, valor) ->
                            Tab(
                                selected = (indice == 1) == esAdmin,
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.onRolChange(valor)
                                },
                                // Sin estos dos, ambas pestañas heredan el
                                // color de marca y no se distingue cuál está
                                // activa más que por la línea de abajo.
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = {
                                    Text(titulo, style = MaterialTheme.typography.labelLarge)
                                }
                            )
                        }
                }

                if (esAdmin) {
                    CampoTexto(
                        valor = uiState.nombre,
                        onValorCambia = viewModel::onNombreChange,
                        etiqueta = "Usuario",
                        icono = Icons.Default.Person,
                        modifier = Modifier.fillMaxWidth(),
                        // Sin esto el teclado escribe "Admin" con mayúscula, y
                        // el servidor compara con igualdad estricta: la
                        // contraseña podía ser correcta y el acceso fallaba
                        // igual, sin ninguna pista de por qué.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
                } else {
                    CampoTexto(
                        valor = uiState.documento,
                        onValorCambia = viewModel::onDocumentoChange,
                        etiqueta = "Documento",
                        icono = Icons.Default.Badge,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
                }

                CampoTexto(
                    valor = uiState.password,
                    onValorCambia = viewModel::onPasswordChange,
                    etiqueta = "Contraseña",
                    icono = Icons.Default.Lock,
                    esPassword = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { ingresar() })
                )

                AnimatedVisibility(visible = uiState.error != null) {
                    MensajeError(
                        mensaje = uiState.error.orEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = ingresar,
                    enabled = !uiState.cargando,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(top = 4.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    // El indicador reemplaza el texto dentro del mismo botón.
                    // Antes sustituía al botón entero y el formulario daba un
                    // salto en cada intento.
                    Box(contentAlignment = Alignment.Center) {
                        if (uiState.cargando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Ingresar", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (!esAdmin) {
            TextButton(
                onClick = onRegisterClick,
                enabled = !uiState.cargando,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "¿No tenés cuenta? Registrate",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
