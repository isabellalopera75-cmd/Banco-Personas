package com.tuapp.bancopersonas.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.bancopersonas.domain.model.Sexo
import com.tuapp.bancopersonas.domain.model.TipoDocumento
import com.tuapp.bancopersonas.ui.components.CampoTexto
import com.tuapp.bancopersonas.ui.components.MensajeError
import com.tuapp.bancopersonas.ui.components.PantallaConTeclado
import com.tuapp.bancopersonas.ui.components.SelectorDesplegable

@Composable
fun PersonaFormScreen(
    personaId: String? = null,
    onGuardada: () -> Unit,
    onCancelar: () -> Unit,
    viewModel: PersonaFormViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    LaunchedEffect(personaId) { viewModel.cargar(personaId) }
    LaunchedEffect(estado.guardado) { if (estado.guardado) onGuardada() }

    PantallaConTeclado { ancho ->
        Text(
            text = if (estado.esEdicion) "Editar persona" else "Registrar persona",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = ancho,
        )
        Text(
            text = "Se guarda en el teléfono y se envía cuando haya señal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = ancho.padding(top = 4.dp),
        )

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = ancho,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SelectorDesplegable(
                valor = estado.tipoDocumento,
                opciones = TipoDocumento.entries,
                etiquetaDe = { "${it.name} — ${it.etiqueta}" },
                onSeleccion = viewModel::cambiarTipoDocumento,
                etiqueta = "Tipo de documento",
                icono = Icons.Default.Badge,
                modifier = ancho,
            )

            CampoTexto(
                valor = estado.numeroDocumento,
                onValorCambia = viewModel::cambiarNumeroDocumento,
                etiqueta = "Número de documento",
                icono = Icons.Default.Numbers,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = ancho,
            )

            CampoTexto(
                valor = estado.primerNombre,
                onValorCambia = viewModel::cambiarPrimerNombre,
                etiqueta = "Primer nombre",
                icono = Icons.Default.Person,
                modifier = ancho,
            )
            CampoTexto(
                valor = estado.segundoNombre,
                onValorCambia = viewModel::cambiarSegundoNombre,
                etiqueta = "Segundo nombre (opcional)",
                icono = Icons.Default.Person,
                modifier = ancho,
            )
            CampoTexto(
                valor = estado.primerApellido,
                onValorCambia = viewModel::cambiarPrimerApellido,
                etiqueta = "Primer apellido",
                icono = Icons.Default.Person,
                modifier = ancho,
            )
            CampoTexto(
                valor = estado.segundoApellido,
                onValorCambia = viewModel::cambiarSegundoApellido,
                etiqueta = "Segundo apellido (opcional)",
                icono = Icons.Default.Person,
                modifier = ancho,
            )

            CampoTexto(
                valor = estado.fechaNacimiento,
                onValorCambia = viewModel::cambiarFechaNacimiento,
                etiqueta = "Fecha de nacimiento",
                icono = Icons.Default.Cake,
                textoAyuda = "AAAA-MM-DD, por ejemplo 1985-03-24",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = ancho,
            )

            SelectorDesplegable(
                valor = estado.sexo,
                opciones = Sexo.entries,
                etiquetaDe = { it.etiqueta },
                onSeleccion = viewModel::cambiarSexo,
                etiqueta = "Sexo",
                icono = Icons.Default.Wc,
                modifier = ancho,
            )

            CampoTexto(
                valor = estado.telefono,
                onValorCambia = viewModel::cambiarTelefono,
                etiqueta = "Teléfono (opcional)",
                icono = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = ancho,
            )

            estado.error?.let { MensajeError(it, ancho) }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = ancho,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancelar,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar") }

                Button(
                    onClick = viewModel::guardar,
                    enabled = !estado.guardando,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                ) {
                    if (estado.guardando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (estado.esEdicion) "Guardar cambios" else "Registrar")
                    }
                }
            }
        }
    }
}
