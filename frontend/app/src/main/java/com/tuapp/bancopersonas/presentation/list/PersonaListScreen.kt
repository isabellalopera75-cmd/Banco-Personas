package com.tuapp.bancopersonas.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.presentation.form.PersonaFormScreen
import com.tuapp.bancopersonas.ui.theme.WinPlayPink
import com.tuapp.bancopersonas.ui.theme.WinPlaySurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaListScreen(
    onLogout: () -> Unit,
    viewModel: PersonaListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarFormulario by remember { mutableStateOf(false) }
    var personaEnEdicion by remember { mutableStateOf<Persona?>(null) }
    var personaAEliminar by remember { mutableStateOf<Persona?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val personasFiltradas = uiState.personas.filter {
        it.nombre.contains(searchQuery, ignoreCase = true) || 
        it.documento.contains(searchQuery, ignoreCase = true)
    }

    if (mostrarFormulario) {
        PersonaFormScreen(
            personaExistente = personaEnEdicion,
            onPersonaGuardada = {
                mostrarFormulario = false
                personaEnEdicion = null
            }
        )
        return
    }

    // Diálogo de confirmación
    personaAEliminar?.let { persona ->
        AlertDialog(
            onDismissRequest = { personaAEliminar = null },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Estás seguro de que deseas eliminar a ${persona.nombre}? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.eliminarPersona(persona.id)
                        personaAEliminar = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = WinPlayPink)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { personaAEliminar = null }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = WinPlaySurface,
            titleContentColor = Color.White,
            textContentColor = Color.LightGray
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    personaEnEdicion = null
                    mostrarFormulario = true
                },
                containerColor = WinPlayPink,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Participante")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
        ) {
            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFC02A35)) // Darker red
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Panel WinPlay", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${uiState.personas.size} participantes", color = Color.White, fontSize = 14.sp)
                        }
                    }
                    IconButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .background(Color(0x33FFFFFF), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Buscar por nombre o documento...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = WinPlaySurface,
                    unfocusedContainerColor = WinPlaySurface,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.cargando) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WinPlayPink)
                }
            } else if (personasFiltradas.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(personasFiltradas) { persona ->
                        PersonaCard(
                            persona = persona,
                            onEditClick = {
                                personaEnEdicion = persona
                                mostrarFormulario = true
                            },
                            onDeleteClick = { personaAEliminar = persona }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.DarkGray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("No hay participantes", style = MaterialTheme.typography.headlineSmall, color = Color.Gray)
        Text("Usa el botón + para empezar", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
    }
}

@Composable
fun PersonaCard(
    persona: Persona,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WinPlaySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2A35))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFFC02A35), CircleShape), // Dark red
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = persona.nombre.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = persona.nombre,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SyncStatusIcon(status = persona.syncStatus)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = persona.documento, color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = persona.telefono, color = Color.Gray, fontSize = 12.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF3A1C22), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = WinPlayPink, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF3A1C22), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SyncStatusIcon(status: SyncStatus) {
    val icon: ImageVector
    val color: Color

    when (status) {
        SyncStatus.SYNCED -> {
            icon = Icons.Default.Cloud
            color = Color(0xFF4CAF50)
        }
        SyncStatus.PENDING -> {
            icon = Icons.Default.Sync
            color = Color(0xFFFF9800)
        }
        SyncStatus.CONFLICT -> {
            icon = Icons.Default.Warning
            color = Color(0xFFF44336)
        }
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(16.dp)
    )
}
