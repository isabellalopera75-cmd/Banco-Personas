package com.tuapp.bancopersonas.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.bancopersonas.domain.model.TipoDocumento
import com.tuapp.bancopersonas.ui.components.CampoTexto
import com.tuapp.bancopersonas.ui.components.MensajeError
import com.tuapp.bancopersonas.ui.components.PantallaConTeclado
import com.tuapp.bancopersonas.ui.components.SelectorDesplegable

/**
 * Dos entradas distintas, no una con un desplegable de rol.
 *
 * Un registrador y una persona del padrón no comparten credencial: uno tiene
 * usuario y contraseña, la otra entra con su documento. Mezclarlos en un solo
 * formulario obligaba a explicar en pantalla cuál de los campos había que
 * llenar según quién fuera.
 */
@Composable
fun LoginScreen(
    onEntrarComoOperador: (rol: String, sinConexion: Boolean) -> Unit,
    onEntrarComoPersona: (personaId: String, sinConexion: Boolean) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val destino by viewModel.destino.collectAsStateWithLifecycle()

    LaunchedEffect(destino) {
        when (val d = destino) {
            is DestinoLogin.Operador -> {
                onEntrarComoOperador(d.rol, d.sinConexion)
                viewModel.destinoConsumido()
            }
            is DestinoLogin.Persona -> {
                onEntrarComoPersona(d.personaId, d.sinConexion)
                viewModel.destinoConsumido()
            }
            null -> Unit
        }
    }

    PantallaConTeclado { anchoCompleto ->
        Text(
            text = "Padrón EPS",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = anchoCompleto,
        )
        Text(
            text = "Registro de personas en campo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = anchoCompleto.padding(top = 4.dp),
        )

        Spacer(Modifier.height(28.dp))

        TabRow(
            selectedTabIndex = estado.modo.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = anchoCompleto,
        ) {
            Tab(
                selected = estado.modo == ModoLogin.OPERADOR,
                onClick = { viewModel.cambiarModo(ModoLogin.OPERADOR) },
                text = { Text("Soy del equipo") },
            )
            Tab(
                selected = estado.modo == ModoLogin.PERSONA,
                onClick = { viewModel.cambiarModo(ModoLogin.PERSONA) },
                text = { Text("Consultar mis datos") },
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(
            modifier = anchoCompleto,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (estado.modo) {
                ModoLogin.OPERADOR -> {
                    CampoTexto(
                        valor = estado.usuario,
                        onValorCambia = viewModel::cambiarUsuario,
                        etiqueta = "Usuario",
                        icono = Icons.Default.Person,
                        modifier = anchoCompleto,
                    )
                    CampoTexto(
                        valor = estado.password,
                        onValorCambia = viewModel::cambiarPassword,
                        etiqueta = "Contraseña",
                        icono = Icons.Default.Lock,
                        esPassword = true,
                        modifier = anchoCompleto,
                    )
                }

                ModoLogin.PERSONA -> {
                    SelectorDesplegable(
                        valor = estado.tipoDocumento,
                        opciones = TipoDocumento.entries,
                        etiquetaDe = { "${it.name} — ${it.etiqueta}" },
                        onSeleccion = viewModel::cambiarTipoDocumento,
                        etiqueta = "Tipo de documento",
                        icono = Icons.Default.Badge,
                        modifier = anchoCompleto,
                    )
                    CampoTexto(
                        valor = estado.numeroDocumento,
                        onValorCambia = viewModel::cambiarNumeroDocumento,
                        etiqueta = "Número de documento",
                        icono = Icons.Default.Numbers,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = anchoCompleto,
                    )
                }
            }

            estado.error?.let { MensajeError(it, anchoCompleto) }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    when (estado.modo) {
                        ModoLogin.OPERADOR -> viewModel.entrarComoOperador()
                        ModoLogin.PERSONA -> viewModel.entrarComoPersona()
                    }
                },
                enabled = !estado.cargando,
                shape = MaterialTheme.shapes.small,
                modifier = anchoCompleto,
            ) {
                if (estado.cargando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Entrar")
                }
            }

            if (estado.modo == ModoLogin.OPERADOR) {
                Text(
                    text = "La primera vez hay que entrar con internet. Después el teléfono " +
                        "te deja trabajar sin señal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = anchoCompleto.padding(top = 8.dp),
                )
            }
        }
    }
}
