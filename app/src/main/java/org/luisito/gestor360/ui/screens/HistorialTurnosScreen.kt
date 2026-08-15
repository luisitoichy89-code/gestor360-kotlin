package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.clickable
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
import org.luisito.gestor360.data.models.TurnoInfo
import org.luisito.gestor360.ui.theme.NeuCard
import org.luisito.gestor360.ui.viewmodels.HistorialTurnosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialTurnosScreen(
    androidId: String,
    onBack: () -> Unit,
    onTurnoClick: (TurnoInfo) -> Unit,
    viewModel: HistorialTurnosViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(androidId) {
        viewModel.cargar(androidId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de turnos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(uiState.error ?: "Error", color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.turnos.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Sin turnos registrados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.turnos, key = { it.id }) { turno ->
                        TurnoCard(turno, onClick = { onTurnoClick(turno) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnoCard(turno: TurnoInfo, onClick: () -> Unit) {
    NeuCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Turno #${turno.numeroTurno}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (turno.cierre == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        if (turno.cierre == null) "Abierto" else "Cerrado",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (turno.cierre == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            turno.usuario_nombre?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Apertura: ${formatearMonto(turno.apertura)} CUP", style = MaterialTheme.typography.bodySmall)
                turno.cierre?.let { cierre ->
                    Text("Cierre: ${formatearMonto(cierre)} CUP", style = MaterialTheme.typography.bodySmall)
                }
            }
            turno.diferencia?.let { diff ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Diferencia: ${formatearMonto(diff)} CUP",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatearMonto(valor: Double): String {
    return if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()
}
