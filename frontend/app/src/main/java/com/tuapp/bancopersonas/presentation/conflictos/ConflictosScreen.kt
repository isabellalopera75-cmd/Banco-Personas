package com.tuapp.bancopersonas.presentation.conflictos

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.tuapp.bancopersonas.data.remote.dto.ConflictoAdminDto
import com.tuapp.bancopersonas.ui.components.CampoTexto
import com.tuapp.bancopersonas.ui.components.Etiqueta
import com.tuapp.bancopersonas.ui.components.MensajeError
import com.tuapp.bancopersonas.ui.theme.PadronFondo
import com.tuapp.bancopersonas.ui.theme.PadronFondoAlto
import com.tuapp.bancopersonas.ui.theme.PadronError
import com.tuapp.bancopersonas.ui.theme.PadronAdvertencia

/**
 * La cola de conflictos del administrador.
 *
 * Es la pantalla que le da salida a los datos que el sistema anterior tiraba:
 * cada fila de acá es trabajo de campo de alguien que el servidor no pudo
 * aplicar solo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictosScreen(
    onVolver: () -> Unit,
    viewModel: ConflictosViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    estado.seleccionado?.let { conflicto ->
        DetalleConflicto(
            conflicto = conflicto,
            campos = estado.campos,
            resolviendo = estado.resolviendo,
            error = estado.error,
            onAlternar = viewModel::alternarCampo,
            onFusionar = viewModel::fusionar,
            onCrearComoNueva = viewModel::crearComoNueva,
            onDescartar = viewModel::descartar,
            onCerrar = viewModel::cerrarDetalle,
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Conflictos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                .padding(horizontal = 16.dp),
        ) {
            when {
                estado.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                estado.conflictos.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        estado.error?.let { MensajeError(it) }
                        if (estado.error == null) {
                            Text(
                                "No hay conflictos pendientes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(estado.conflictos, key = { it.id }) { conflicto ->
                        TarjetaConflicto(conflicto) { viewModel.seleccionar(conflicto) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaConflicto(conflicto: ConflictoAdminDto, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Etiqueta(
                    texto = if (conflicto.tipo == "ALTA_DUPLICADA") "Alta duplicada"
                    else "Edición simultánea",
                    color = if (conflicto.tipo == "ALTA_DUPLICADA") PadronError else PadronAdvertencia,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    conflicto.enviadoPor.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            val existente = conflicto.personaExistente
            Text(
                text = listOfNotNull(
                    existente?.primerNombre,
                    existente?.primerApellido,
                ).joinToString(" ").ifBlank { "Registro sin nombre" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${existente?.tipoDocumento.orEmpty()} ${existente?.numeroDocumento.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetalleConflicto(
    conflicto: ConflictoAdminDto,
    campos: List<CampoEnfrentado>,
    resolviendo: Boolean,
    error: String?,
    onAlternar: (String) -> Unit,
    onFusionar: (String) -> Unit,
    onCrearComoNueva: (String, String) -> Unit,
    onDescartar: (String) -> Unit,
    onCerrar: () -> Unit,
) {
    var nota by remember { mutableStateOf("") }
    var separando by remember { mutableStateOf(false) }
    var tipoNuevo by remember { mutableStateOf(conflicto.datosEnviados["tipo_documento"].orEmpty()) }
    var numeroNuevo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Text(
                if (conflicto.tipo == "ALTA_DUPLICADA") "Alta duplicada" else "Edición simultánea",
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${conflicto.enviadoPor} registró estos datos y chocaron con lo que " +
                        "ya estaba guardado. Marcá los campos que quieras traer del intento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                campos.filter { it.hayDiferencia }.forEach { campo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAlternar(campo.campo) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = campo.elegido, onCheckedChange = { onAlternar(campo.campo) })
                        Column(Modifier.weight(1f)) {
                            Text(campo.etiqueta, style = MaterialTheme.typography.labelMedium)
                            Text(
                                "Guardado: ${campo.valorActual ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Llegó: ${campo.valorEnviado ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = PadronAdvertencia,
                            )
                        }
                    }
                }

                if (campos.none { it.hayDiferencia }) {
                    Text(
                        "El intento no aporta ningún dato distinto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (separando) {
                    Text(
                        "Son dos personas distintas: escribí el documento corregido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    CampoTexto(
                        valor = tipoNuevo,
                        onValorCambia = { tipoNuevo = it.uppercase() },
                        etiqueta = "Tipo (CC, TI, RC…)",
                        icono = Icons.Default.CallSplit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    CampoTexto(
                        valor = numeroNuevo,
                        onValorCambia = { numeroNuevo = it },
                        etiqueta = "Número corregido",
                        icono = Icons.Default.CallSplit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CampoTexto(
                        valor = nota,
                        onValorCambia = { nota = it },
                        etiqueta = "Nota (obligatoria para descartar)",
                        icono = Icons.Default.Merge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    MensajeError(it, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (separando) {
                Button(
                    onClick = { onCrearComoNueva(tipoNuevo.trim(), numeroNuevo.trim()) },
                    enabled = !resolviendo && numeroNuevo.isNotBlank() && tipoNuevo.isNotBlank(),
                ) { Text("Crear como nueva") }
            } else {
                Button(onClick = { onFusionar(nota) }, enabled = !resolviendo) {
                    if (resolviendo) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Fusionar")
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCerrar) { Text("Cerrar") }

                // Separar solo tiene sentido en un alta duplicada: en una
                // edición simultánea no hay dos personas, hay dos versiones
                // de la misma.
                if (conflicto.tipo == "ALTA_DUPLICADA") {
                    TextButton(onClick = { separando = !separando }) {
                        Text(if (separando) "Volver" else "Son distintas")
                    }
                }

                TextButton(
                    onClick = { onDescartar(nota) },
                    enabled = !resolviendo && !separando,
                ) { Text("Descartar", color = PadronError) }
            }
        },
    )
}
