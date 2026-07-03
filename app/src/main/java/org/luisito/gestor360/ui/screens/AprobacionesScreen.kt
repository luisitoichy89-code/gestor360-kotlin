package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.MermaPendiente
import org.luisito.gestor360.ui.components.EstadoCargando
import org.luisito.gestor360.ui.components.EstadoError
import org.luisito.gestor360.ui.components.EstadoVacio
import org.luisito.gestor360.ui.viewmodels.MermaViewModel

/**
 * Solo el admin ve esta pantalla. Aquí aprueba o rechaza las mermas que los
 * vendedores propusieron desde ProductosScreen. Aprobar descuenta el stock real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AprobacionesScreen(
    clienteId: String,
    adminUsuarioId: Long,
    onBack: (() -> Unit)? = null,
    viewModel: MermaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(clienteId) { viewModel.cargarPendientes(clienteId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aprobaciones de merma") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, contentDescription = "Refrescar") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                uiState.pendientes.isEmpty() -> EstadoVacio("No hay mermas pendientes de aprobar")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.pendientes, key = { it.id }) { merma ->
                        MermaCard(
                            merma = merma,
                            isSaving = uiState.isSaving,
                            onAprobar = { viewModel.aprobar(merma, adminUsuarioId) },
                            onRechazar = { viewModel.rechazar(merma, adminUsuarioId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MermaCard(
    merma: MermaPendiente,
    isSaving: Boolean,
    onAprobar: () -> Unit,
    onRechazar: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(merma.producto_nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Cantidad solicitada: ${merma.cantidad}")
            if (!merma.motivo.isNullOrBlank()) {
                Text("Motivo: ${merma.motivo}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Solicitado por: ${merma.solicitado_por_nombre ?: "usuario #${merma.solicitado_por}"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAprobar, enabled = !isSaving, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Aprobar")
                }
                OutlinedButton(
                    onClick = onRechazar,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rechazar")
                }
            }
        }
    }
}
