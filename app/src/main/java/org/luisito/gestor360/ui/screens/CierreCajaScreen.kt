package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.ui.components.EstadoCargando
import org.luisito.gestor360.ui.components.EstadoError
import org.luisito.gestor360.ui.components.EstadoVacio
import org.luisito.gestor360.ui.viewmodels.CierreCajaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CierreCajaScreen(
    androidId: String,
    onBack: (() -> Unit)? = null,
    viewModel: CierreCajaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarAbrirTurno by remember { mutableStateOf(false) }
    var mostrarCerrarTurno by remember { mutableStateOf(false) }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    // Confirmación al abrir turno
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cierre de caja") },
                navigationIcon = { if (onBack != null) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } } },
                actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, contentDescription = "Refrescar") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Mensaje de éxito
            uiState.mensaje?.let { mensaje ->

            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                uiState.turnoActivo == null -> TurnoCerradoPanel(isSaving = uiState.isSaving, onAbrir = { mostrarAbrirTurno = true })
                else -> TurnoAbiertoPanel(
                    apertura = uiState.turnoActivo!!.apertura,
                    productosVendidos = uiState.productosVendidos,
                    totalEfectivo = uiState.totalEfectivo,
                    totalTransferencia = uiState.totalTransferencia,
                    totalMixto = uiState.totalMixto,
                    onCerrar = { mostrarCerrarTurno = true }
                )
            }
        }
    }

    if (mostrarAbrirTurno) {
        AbrirTurnoDialog(isSaving = uiState.isSaving, onDismiss = { mostrarAbrirTurno = false }, onAbrir = { apertura -> viewModel.abrirTurno(apertura); mostrarAbrirTurno = false })
    }

    if (mostrarCerrarTurno) {
        CerrarTurnoDialog(efectivoEsperado = (uiState.turnoActivo?.apertura ?: 0.0) + uiState.totalEfectivo, isSaving = uiState.isSaving, onDismiss = { mostrarCerrarTurno = false }, onCerrar = { cierre -> viewModel.cerrarTurno(cierre); mostrarCerrarTurno = false })
    }

    uiState.turnoRecienCerrado?.let { turno ->
        val diferencia = turno.diferencia ?: 0.0
        AlertDialog(
            onDismissRequest = { viewModel.limpiarTurnoRecienCerrado() },
            title = { Text("Turno cerrado") },
            text = {
                Column {
                    Text("Efectivo contado: ${turno.cierre} CUP")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        when {
                            diferencia > 0 -> "Sobran ${diferencia} CUP"
                            diferencia < 0 -> "Faltan ${-diferencia} CUP"
                            else -> "Cuadra exacto ✅"
                        },
                        fontWeight = FontWeight.Bold,
                        color = when { diferencia > 0 -> MaterialTheme.colorScheme.tertiary; diferencia < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.limpiarTurnoRecienCerrado() }) { Text("Entendido") } }
        )
    }
}

@Composable
private fun TurnoCerradoPanel(isSaving: Boolean, onAbrir: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text("No tienes un turno abierto", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAbrir, enabled = !isSaving) { Text("Abrir turno") }
    }
}

@Composable
private fun TurnoAbiertoPanel(apertura: Double, productosVendidos: List<Pair<String, Double>>, totalEfectivo: Double, totalTransferencia: Double, totalMixto: Double, onCerrar: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                FilaResumen("Apertura", apertura)
                FilaResumen("Efectivo vendido", totalEfectivo)
                FilaResumen("Transferencia", totalTransferencia)
                FilaResumen("Mixto", totalMixto)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                FilaResumen("Total en caja esperado", apertura + totalEfectivo, destacado = true)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Productos vendidos en este turno", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        if (productosVendidos.isEmpty()) EstadoVacio("Aún no hay ventas en este turno")
        else LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(productosVendidos) { (nombre, cantidad) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(nombre)
                    Text(if (cantidad == cantidad.toLong().toDouble()) cantidad.toLong().toString() else cantidad.toString(), fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cerrar turno")
        }
    }
}

@Composable
private fun FilaResumen(etiqueta: String, valor: Double, destacado: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text("$valor CUP", style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal, color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AbrirTurnoDialog(isSaving: Boolean, onDismiss: () -> Unit, onAbrir: (Double) -> Unit) {
    var monto by remember { mutableStateOf("") }
    val apertura = monto.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir turno") },
        text = {
            OutlinedTextField(value = monto, onValueChange = { monto = it.filter { c -> c.isDigit() } }, label = { Text("Efectivo inicial en caja") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(enabled = apertura != null && apertura >= 0 && !isSaving, onClick = { onAbrir(apertura ?: 0.0) }) { Text(if (isSaving) "Abriendo..." else "Abrir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun CerrarTurnoDialog(efectivoEsperado: Double, isSaving: Boolean, onDismiss: () -> Unit, onCerrar: (Double) -> Unit) {
    var monto by remember { mutableStateOf("") }
    val contado = monto.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar turno") },
        text = {
            Column {
                Text("Efectivo esperado en caja: $efectivoEsperado CUP", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = monto, onValueChange = { monto = it.filter { c -> c.isDigit() } }, label = { Text("Efectivo contado (real)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = contado != null && contado >= 0 && !isSaving, onClick = { onCerrar(contado ?: 0.0) }) { Text(if (isSaving) "Cerrando..." else "Cerrar turno") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
