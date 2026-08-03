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
import org.luisito.gestor360.data.models.Devolucion
import org.luisito.gestor360.ui.components.*
import org.luisito.gestor360.ui.viewmodels.DevolucionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevolucionScreen(androidId: String, onBack: (() -> Unit)? = null, viewModel: DevolucionViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var devolucionAAprobar by remember { mutableStateOf<Devolucion?>(null) }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Devolución", fontWeight = FontWeight.Bold) }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
                uiState.pendientes.isEmpty() -> EstadoVacio("No hay devoluciones pendientes")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.pendientes, key = { it.id }) { dev ->
                        DevolucionCard(dev, uiState.isSaving, onAprobar = { devolucionAAprobar = dev })
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    devolucionAAprobar?.let { dev ->
        AlertDialog(
            onDismissRequest = { devolucionAAprobar = null },
            shape = RoundedCornerShape(18.dp),
            title = { Text("¿A dónde va \"${dev.producto_nombre}\"?", fontWeight = FontWeight.Bold) },
            text = { Text("Si el producto se puede volver a vender, va a stock. Si no sirve, se registra como merma y no vuelve a venderse.") },
            confirmButton = { TextButton(onClick = { viewModel.aprobar(dev.id, "stock"); devolucionAAprobar = null }) { Text("Vuelve a stock") } },
            dismissButton = { TextButton(onClick = { viewModel.aprobar(dev.id, "merma"); devolucionAAprobar = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("No sirve → merma") } }
        )
    }
}

@Composable
private fun DevolucionCard(dev: Devolucion, isSaving: Boolean, onAprobar: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AssignmentReturn, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(dev.producto_nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Cantidad: ${dev.cantidad}  ·  Método: ${dev.metodo}", fontWeight = FontWeight.Medium)
            if (!dev.motivo.isNullOrBlank()) { Spacer(Modifier.height(4.dp)); Text("Motivo: ${dev.motivo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(6.dp))
            Text("Solicitado por: ${dev.solicitado_por_nombre ?: "Usuario #${dev.solicitado_por}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!dev.created_at.isNullOrBlank()) Text(dev.created_at.take(16).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAprobar, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Aprobar") }
        }
    }
}
