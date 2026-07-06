package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.MermaPendiente
import org.luisito.gestor360.ui.components.*
import org.luisito.gestor360.ui.viewmodels.MermaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AprobacionesScreen(androidId: String, onBack: (() -> Unit)? = null, viewModel: MermaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(androidId) { viewModel.cargarPendientes(androidId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Aprobaciones generales", fontWeight = FontWeight.Bold) }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
                uiState.pendientes.isEmpty() -> EstadoVacio("No hay solicitudes pendientes")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.pendientes, key = { it.id }) { merma -> MermaCard(merma, uiState.isSaving, { viewModel.aprobar(merma) }, { viewModel.rechazar(merma) }) }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MermaCard(merma: MermaPendiente, isSaving: Boolean, onAprobar: () -> Unit, onRechazar: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Text(merma.producto_nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Cantidad: ${merma.cantidad}", fontWeight = FontWeight.Medium)
            if (!merma.motivo.isNullOrBlank()) { Spacer(Modifier.height(4.dp)); Text("Motivo: ${merma.motivo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(6.dp))
            Text("Solicitado por: ${merma.solicitado_por_nombre ?: "Usuario #${merma.solicitado_por}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAprobar, enabled = !isSaving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Aprobar") }
                OutlinedButton(onClick = onRechazar, enabled = !isSaving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text("Rechazar") }
            }
        }
    }
}
