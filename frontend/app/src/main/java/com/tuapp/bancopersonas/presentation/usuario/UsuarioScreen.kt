package com.tuapp.bancopersonas.presentation.usuario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tuapp.bancopersonas.ui.theme.WinPlayPink
import com.tuapp.bancopersonas.ui.theme.WinPlaySurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsuarioScreen(
    personaId: String,
    onLogout: () -> Unit,
    viewModel: UsuarioViewModel = hiltViewModel()
) {
    LaunchedEffect(personaId) {
        viewModel.init(personaId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val persona = uiState.persona

    if (persona == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = WinPlayPink)
        }
        return
    }

    Scaffold(
        floatingActionButton = {
            if (!uiState.isEditing) {
                FloatingActionButton(
                    onClick = { viewModel.setEditing(true) },
                    containerColor = WinPlayPink
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Color.White)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Top Bar Custom
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .border(1.dp, WinPlayPink, CircleShape)
                            .background(Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = persona.nombre.split(" ").take(2).joinToString("") { it.take(1) }.uppercase()
                        Text(initials, color = WinPlayPink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MI FICHA", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Personal", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onLogout,
                        border = androidx.compose.foundation.BorderStroke(1.dp, WinPlayPink),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WinPlayPink)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salir")
                    }
                }
            }

            // Ganador Semanal Card
            item {
                if (uiState.isOffline) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            "Sin conexión. Conéctate a internet para descubrir al ganador de la semana.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                } else if (!uiState.isLoadingGanador && uiState.ganadorSemana != null) {
                    val ganador = uiState.ganadorSemana!!
                    val esGanador = ganador.id == persona.id
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF550011), Color(0xFF1A0000))
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(WinPlayPink, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    if (esGanador) "¡ERES EL GANADOR DE LA SEMANA!" else "GANADOR DE LA SEMANA",
                                    color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (esGanador) "¡Felicidades!" else ganador.nombre,
                                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Datos personales
            item {
                if (uiState.isEditing) {
                    var nombre by remember { mutableStateOf(persona.nombre) }
                    var documento by remember { mutableStateOf(persona.documento) }
                    var telefono by remember { mutableStateOf(persona.telefono) }

                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = WinPlaySurface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Editar Datos", style = MaterialTheme.typography.titleLarge, color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = nombre, onValueChange = { nombre = it },
                                label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = documento, onValueChange = { documento = it },
                                label = { Text("Documento") }, modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = telefono, onValueChange = { telefono = it },
                                label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.setEditing(false) }) { Text("Cancelar", color = Color.LightGray) }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { viewModel.guardarCambios(nombre, documento, telefono) }, colors = ButtonDefaults.buttonColors(containerColor = WinPlayPink)) {
                                    Text("Guardar", color = Color.White)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Datos personales", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        
                        // Sincronizado Pill
                        val isSynced = persona.syncStatus.name == "SYNCED"
                        val syncColor = if (isSynced) Color(0xFF4CAF50) else Color(0xFFF44336)
                        val syncBg = if (isSynced) Color(0xFF1E3320) else Color(0xFF331E1E)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(syncBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = syncColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSynced) "Sincronizado" else "Pendiente", color = syncColor, fontSize = 12.sp)
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WinPlaySurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            DatoRow(icon = Icons.Default.Person, label = "NOMBRE", value = persona.nombre)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF2A2A35))
                            DatoRow(icon = Icons.Default.CreditCard, label = "DOCUMENTO", value = persona.documento)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF2A2A35))
                            DatoRow(icon = Icons.Default.Phone, label = "TELÉFONO", value = persona.telefono)
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Historial
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = WinPlayPink, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Historial de cambios", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                if (uiState.historial.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF2A2A35), RoundedCornerShape(16.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color(0xFF2A1A1A), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Aún no hay cambios", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Cuando edites tus datos, verás el registro aquí.",
                                color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (uiState.historial.isNotEmpty()) {
                items(uiState.historial) { hist ->
                    val fecha = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                        val d = sdf.parse(hist.creadoEn)
                        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(d ?: Date())
                    } catch (e: Exception) {
                        hist.creadoEn
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = WinPlaySurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Fecha: $fecha", style = MaterialTheme.typography.labelMedium, color = WinPlayPink)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Nombre: ${hist.nombreAnterior}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text("Documento: ${hist.documentoAnterior}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text("Teléfono: ${hist.telefonoAnterior}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.revertirA(hist) },
                                modifier = Modifier.align(Alignment.End),
                                enabled = !uiState.isEditing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A35))
                            ) {
                                Text("Revertir a esta versión", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DatoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF25252D), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}