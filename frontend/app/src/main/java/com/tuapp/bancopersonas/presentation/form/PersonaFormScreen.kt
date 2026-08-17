package com.tuapp.bancopersonas.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.ui.theme.WinPlayPink
import com.tuapp.bancopersonas.ui.theme.WinPlaySurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaFormScreen(
    personaExistente: Persona? = null,
    onPersonaGuardada: () -> Unit,
    viewModel: PersonaFormViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.iniciarFormulario(personaExistente)
    }

    Scaffold(
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Custom Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPersonaGuardada,
                    modifier = Modifier
                        .background(WinPlaySurface, CircleShape)
                        .size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (uiState.esEdicion) "Editar Participante" else "Nuevo Participante",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = WinPlayPink, spotColor = WinPlayPink),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = WinPlaySurface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Datos del Participante",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = WinPlayPink,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E24),
                        unfocusedContainerColor = Color(0xFF1E1E24),
                        focusedBorderColor = WinPlayPink,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = WinPlayPink,
                        unfocusedLabelColor = Color.Gray,
                        focusedLeadingIconColor = WinPlayPink,
                        unfocusedLeadingIconColor = Color.Gray
                    )

                    OutlinedTextField(
                        value = uiState.nombre,
                        onValueChange = { viewModel.actualizarNombre(it) },
                        label = { Text("Nombre Completo") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.documento,
                        onValueChange = { viewModel.actualizarDocumento(it) },
                        label = { Text("Documento de Identidad") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.telefono,
                        onValueChange = { viewModel.actualizarTelefono(it) },
                        label = { Text("Teléfono de Contacto") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = textFieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))

            val context = androidx.compose.ui.platform.LocalContext.current

            Button(
                onClick = {
                    viewModel.guardar(onExito = {
                        val mensaje = if (uiState.esEdicion) "Cambios guardados exitosamente" else "¡Registro exitoso! Ya puedes iniciar sesión"
                        android.widget.Toast.makeText(context, mensaje, android.widget.Toast.LENGTH_SHORT).show()
                        onPersonaGuardada()
                    })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(8.dp, RoundedCornerShape(30.dp), ambientColor = WinPlayPink, spotColor = WinPlayPink),
                enabled = uiState.nombre.isNotBlank() && uiState.documento.isNotBlank(),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WinPlayPink,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF550011),
                    disabledContentColor = Color.Gray
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (uiState.esEdicion) "Guardar Cambios" else "Registrar Participante",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
