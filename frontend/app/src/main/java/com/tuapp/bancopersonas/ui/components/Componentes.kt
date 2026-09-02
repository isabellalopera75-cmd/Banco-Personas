package com.tuapp.bancopersonas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.tuapp.bancopersonas.ui.theme.WinPlayDarkBg
import com.tuapp.bancopersonas.ui.theme.WinPlayDarkBgTop

/**
 * Contenedor de pantalla para formularios.
 *
 * Resuelve el motivo por el que el teclado tapaba la contraseña: el contenido
 * va dentro de un scroll y con [imePadding], así el alto disponible se reduce
 * cuando el teclado aparece en vez de quedar oculto debajo. Con el scroll
 * presente, Compose además desplaza solo el campo que toma el foco.
 *
 * Cuando el contenido entra completo queda centrado; recién cuando no entra
 * empieza a desplazarse.
 */
@Composable
fun PantallaConTeclado(
    modifier: Modifier = Modifier,
    contenido: @Composable (Modifier) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(WinPlayDarkBgTop, WinPlayDarkBg)))
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        contenido(Modifier.fillMaxWidth())
    }
}

/**
 * Campo de texto con el estilo de la aplicación.
 *
 * Cuando [esPassword] es cierto agrega el ojo para mostrar y ocultar: escribir
 * a ciegas una contraseña de ocho caracteres en un teclado de teléfono es la
 * forma más rápida de equivocarse sin enterarse.
 */
@Composable
fun CampoTexto(
    valor: String,
    onValorCambia: (String) -> Unit,
    etiqueta: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    esPassword: Boolean = false,
    textoAyuda: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = valor,
        onValueChange = onValorCambia,
        label = { Text(etiqueta) },
        leadingIcon = { Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (esPassword) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) "Ocultar contraseña" else "Mostrar contraseña",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else null,
        visualTransformation = when {
            !esPassword || visible -> VisualTransformation.None
            else -> PasswordVisualTransformation()
        },
        supportingText = textoAyuda?.let {
            { Text(it, style = MaterialTheme.typography.bodySmall) }
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            // El contenedor relleno y el borde apagado sustituyen al borde
            // rosa permanente: solo el campo enfocado toma el color de marca.
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * Mensaje de error.
 *
 * Va dentro de un bloque tenue del mismo tono en lugar de ser un renglón rojo
 * suelto: se lee como una advertencia y no como una alarma.
 */
@Composable
fun MensajeError(mensaje: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = mensaje,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
