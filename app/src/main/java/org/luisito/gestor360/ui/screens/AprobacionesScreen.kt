package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.ui.viewmodels.MermaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AprobacionesScreen(
    androidId: String,
    usuarioId: String,
    onBack: () -> Unit,
    viewModel: MermaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(androidId) { viewModel.cargarPendientes(androidId) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Aprobaciones") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.error != null -> Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                uiState.mermas.isEmpty() -> Text("No hay mermas pendientes")
                else -> LazyColumn {
                    items(uiState.mermas) { m ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("${m.producto_nombre} x${m.cantidad} · ${m.solicitado_por_nombre}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.resolver(m.id.toString(), "aprobada", usuarioId) }) { Text("Aprobar") }
                                    OutlinedButton(onClick = { viewModel.resolver(m.id.toString(), "rechazada", usuarioId) }) { Text("Rechazar") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
