package com.tuapp.bancopersonas.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.ui.components.CampoTexto
import com.tuapp.bancopersonas.ui.components.Etiqueta
import com.tuapp.bancopersonas.ui.theme.WinPlayDarkBg
import com.tuapp.bancopersonas.ui.theme.WinPlayDarkBgTop
import com.tuapp.bancopersonas.ui.theme.WinPlayError
import com.tuapp.bancopersonas.ui.theme.WinPlayPinkSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaListScreen(
    onNuevaPersona: () -> Unit,
    onEditarPersona: (String) -> Unit,
    onVerConflictos: () -> Unit,
    onSalir: () -> Unit,
    viewModel: PersonaListViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val personas by viewModel.personas.collectAsStateWithLifecycle()

    var aEliminar by remember { mutableStateOf<Persona?>(null) }

    estado.bloqueoCierreSesion?.let { mensaje ->
        AlertDialog(
            onDismissRequest = viewModel::bloqueoConsumido,
            title = { Text("No se puede cerrar sesión todavía") },
            text = { Text(mensaje) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.bloqueoConsumido()
                    viewModel.sincronizar()
                }) { Text("Sincronizar ahora") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::bloqueoConsumido) { Text("Entendido") }
            },
        )
    }

    aEliminar?.let { persona ->
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            title = { Text("Dar de baja") },
            text = { Text("¿Dar de baja a ${persona.nombreCompleto}? Queda registrado en el historial.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.eliminar(persona.id)
                    aEliminar = null
                }) { Text("Dar de baja") }
            },
            dismissButton = { TextButton(onClick = { aEliminar = null }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (viewModel.esAdmin) "Padrón completo" else "Mis registros",
                            fontWeight = FontWeight.Bold,
                        )
                        if (viewModel.nombreUsuario.isNotBlank()) {
                            Text(
                                viewModel.nombreUsuario,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (viewModel.esAdmin) {
                        IconButton(onClick = onVerConflictos) {
                            Icon(Icons.Default.ReportProblem, contentDescription = "Conflictos")
                        }
                    }
                    IconButton(onClick = viewModel::sincronizar) {
                        Icon(Icons.Default.Sync, contentDescription = "Sincronizar")
                    }
                    IconButton(onClick = { viewModel.cerrarSesion(onSalir) }) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNuevaPersona,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Registrar") },
                shape = MaterialTheme.shapes.small,
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(WinPlayDarkBgTop, WinPlayDarkBg)))
                .padding(relleno)
                .padding(horizontal = 16.dp),
        ) {
            BarraEstado(
                pendientes = estado.pendientes,
                sinConexion = estado.sinConexion,
                enRevision = personas.count { it.syncStatus == SyncStatus.EN_REVISION },
            )

            CampoTexto(
                valor = estado.busqueda,
                onValorCambia = viewModel::cambiarBusqueda,
                etiqueta = "Buscar por nombre o documento",
                icono = Icons.Default.Search,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )

            if (personas.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (estado.busqueda.isBlank())
                            "Todavía no registraste a nadie.\nTocá «Registrar» para empezar."
                        else "Ningún registro coincide con la búsqueda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(personas, key = { it.id }) { persona ->
                        TarjetaPersona(
                            persona = persona,
                            onEditar = { onEditarPersona(persona.id) },
                            onEliminar = { aEliminar = persona },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BarraEstado(pendientes: Int, sinConexion: Boolean, enRevision: Int) {
    if (pendientes == 0 && !sinConexion && enRevision == 0) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (sinConexion) {
                LineaEstado(
                    Icons.Default.CloudOff,
                    "Sin conexión. Podés seguir registrando: se envía solo cuando haya señal.",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pendientes > 0) {
                LineaEstado(
                    Icons.Default.CloudUpload,
                    "$pendientes cambio(s) esperando para enviarse.",
                    WinPlayPinkSoft,
                )
            }
            if (enRevision > 0) {
                LineaEstado(
                    Icons.Default.ReportProblem,
                    "$enRevision registro(s) en revisión del administrador. " +
                        "Los datos están guardados en el servidor.",
                    WinPlayError,
                )
            }
        }
    }
}

@Composable
private fun LineaEstado(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun TarjetaPersona(persona: Persona, onEditar: () -> Unit, onEliminar: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEditar),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    persona.nombreCompleto,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    persona.documentoCompleto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                EtiquetaSync(persona.syncStatus)
            }

            IconButton(onClick = onEditar) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onEliminar) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Dar de baja",
                    tint = WinPlayError,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * `EN_REVISION` tiene su propio texto y no dice "error".
 *
 * No es un fallo del registrador ni un dato perdido: el servidor lo recibió,
 * no lo pudo aplicar solo y lo dejó esperando una decisión. Decirlo así evita
 * que alguien vuelva a cargar el mismo registro creyendo que se perdió.
 */
@Composable
private fun EtiquetaSync(estado: SyncStatus) = when (estado) {
    SyncStatus.SYNCED -> Etiqueta("Guardado", Color(0xFF6FCF97), Icons.Default.CheckCircle)
    SyncStatus.PENDING -> Etiqueta("Sin enviar", WinPlayPinkSoft, Icons.Default.HourglassEmpty)
    SyncStatus.EN_REVISION ->
        Etiqueta("En revisión del administrador", WinPlayError, Icons.Default.ReportProblem)
}
