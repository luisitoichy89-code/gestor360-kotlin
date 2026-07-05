package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.local.entities.ConflictoEntity
import org.luisito.gestor360.ui.components.EstadoVacio
import org.luisito.gestor360.ui.viewmodels.SyncViewModel

/**
 * Cosas que la sincronización detectó y no resolvió sola (ej. dos vendedores
 * descontaron el mismo producto estando ambos sin conexión y el stock quedó
 * negativo). El admin decide qué hacer y marca cada una como resuelta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictosScreen(
    onBack: (() -> Unit)? = null,
    viewModel: SyncViewModel = viewModel()
) {
    val conflictos by viewModel.conflictos.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conflictos de sincronización") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (conflictos.isEmpty()) {
                EstadoVacio("Sin conflictos pendientes")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(conflictos, key = { it.id }) { conflicto ->
                        ConflictoCard(conflicto, onResolver = { viewModel.resolverConflicto(conflicto.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictoCard(conflicto: ConflictoEntity, onResolver: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(conflicto.tipo, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(conflicto.descripcion, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onResolver) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Marcar como revisado")
            }
        }
    }
}
