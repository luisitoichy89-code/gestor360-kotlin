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
import org.luisito.gestor360.ui.viewmodels.TurnoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CierreCajaScreen(
    androidId: String,
    usuarioId: Long,
    almacenId: String,
    onBack: () -> Unit,
    viewModel: TurnoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarAbrirDialog by remember { mutableStateOf(false) }
    var efectivoInicial by remember { mutableStateOf("0") }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cierre de Caja") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.error != null -> Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                uiState.turnoAbierto != null -> {
                    val t = uiState.turnoAbierto!!
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Turno abierto", style = MaterialTheme.typography.titleMedium)
                            Text("Inicio: ${t.apertura?.take(16)?.replace("T", " ")}")
                            Text("Efectivo inicial: ${t.efectivo_inicial} CUP")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.cerrarTurno(t.id!!) }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar Turno") }
                        }
                    }
                }
                else -> {
                    Text("No hay turno abierto", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { mostrarAbrirDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Abrir Turno") }
                }
            }

            if (uiState.resumenCierre != null) {
                Spacer(modifier = Modifier.height(16.dp))
                val r = uiState.resumenCierre!!
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resumen del turno", style = MaterialTheme.typography.titleMedium)
                        Text("Ventas: ${r.total_ventas} CUP")
                        Text("Efectivo: ${r.total_efectivo} CUP")
                        Text("Transferencia: ${r.total_transferencia} CUP")
                        Text("Diferencia: ${r.diferencia} CUP")
                        Button(onClick = { viewModel.clearResumen() }) { Text("OK") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Historial", style = MaterialTheme.typography.titleSmall)
            if (uiState.historial.isEmpty()) Text("Sin turnos anteriores")
            else LazyColumn { items(uiState.historial) { t ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${t.apertura?.take(10)} - ${t.cierre?.take(10) ?: "Abierto"}")
                        Text("Ventas: ${t.total_ventas} CUP · Dif: ${t.diferencia} CUP", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }}
        }
    }

    if (mostrarAbrirDialog) {
        AlertDialog(
            onDismissRequest = { mostrarAbrirDialog = false },
            title = { Text("Abrir turno") },
            text = { OutlinedTextField(efectivoInicial, { efectivoInicial = it }, label = { Text("Efectivo inicial (CUP)") }) },
            confirmButton = { TextButton(onClick = { viewModel.abrirTurno(efectivoInicial.toDoubleOrNull() ?: 0.0, usuarioId, almacenId); mostrarAbrirDialog = false }) { Text("Abrir") } },
            dismissButton = { TextButton(onClick = { mostrarAbrirDialog = false }) { Text("Cancelar") } }
        )
    }
}
