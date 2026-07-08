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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.ui.components.*
import org.luisito.gestor360.ui.viewmodels.CierreCajaViewModel
import org.luisito.gestor360.utils.CsvExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CierreCajaScreen(
    androidId: String, onBack: (() -> Unit)? = null,
    viewModel: CierreCajaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var mostrarAbrirTurno by remember { mutableStateOf(false) }
    var mostrarCerrarTurno by remember { mutableStateOf(false) }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Text("Cierre de caja", fontWeight = FontWeight.Bold) }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, null) }; if (uiState.turnoActivo != null) IconButton(onClick = { CsvExporter.exportarCierreCaja(context, uiState.turnoActivo?.created_at?.take(10) ?: "", uiState.productosVendidos, uiState.totalEfectivo, uiState.totalTransferencia, uiState.totalMixto, uiState.totalMixtoEfectivo, uiState.totalMixtoTransferencia, uiState.turnoActivo?.apertura ?: 0.0) }) { Icon(Icons.Default.FileDownload, null) } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
                uiState.turnoActivo == null -> TurnoCerradoPanel(uiState.isSaving) { mostrarAbrirTurno = true }
                else -> TurnoAbiertoPanel(uiState.turnoActivo?.apertura ?: 0.0, uiState.productosVendidos, uiState.totalEfectivo, uiState.totalTransferencia, uiState.totalMixto, uiState.totalMixtoEfectivo, uiState.totalMixtoTransferencia) { mostrarCerrarTurno = true }
            }
        }
    }

    if (mostrarAbrirTurno) AbrirTurnoDialog(uiState.isSaving, { mostrarAbrirTurno = false }) { viewModel.abrirTurno(it); mostrarAbrirTurno = false }
    if (mostrarCerrarTurno) CerrarTurnoDialog((uiState.turnoActivo?.apertura ?: 0.0) + uiState.efectivoEnCaja, uiState.isSaving, { mostrarCerrarTurno = false }) { viewModel.cerrarTurno(it); mostrarCerrarTurno = false }

    uiState.turnoRecienCerrado?.let { turno ->
        val dif = turno.diferencia ?: 0.0
        AlertDialog(onDismissRequest = { viewModel.limpiarTurnoRecienCerrado() }, title = { Text("Turno cerrado") }, text = { Column { Text("Efectivo contado: ${turno.cierre} CUP"); Text(when { dif > 0 -> "Sobran $dif CUP"; dif < 0 -> "Faltan ${-dif} CUP"; else -> "Cuadra exacto ✅" }, fontWeight = FontWeight.Bold, color = when { dif > 0 -> MaterialTheme.colorScheme.tertiary; dif < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }) } }, confirmButton = { TextButton(onClick = { viewModel.limpiarTurnoRecienCerrado() }) { Text("Entendido") } })
    }
}

@Composable
private fun TurnoCerradoPanel(isSaving: Boolean, onAbrir: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("No tienes un turno abierto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Abre un turno para comenzar el control de caja", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAbrir, enabled = !isSaving, shape = RoundedCornerShape(14.dp)) { Text("Abrir turno") }
    }
}

@Composable
private fun TurnoAbiertoPanel(apertura: Double, productosVendidos: List<Pair<String, Double>>, totalEfectivo: Double, totalTransferencia: Double, totalMixto: Double, totalMixtoEfectivo: Double, totalMixtoTransferencia: Double, onCerrar: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Resumen del turno", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                FilaResumen("Apertura", apertura)
                FilaResumen("Efectivo vendido", totalEfectivo)
                FilaResumen("Transferencia", totalTransferencia)
                FilaResumen("Mixto", totalMixto)
                if (totalMixto > 0.0) {
                    // Desglose de lo que hay dentro de "Mixto": antes se veía un solo
                    // monto y no se sabía cuánto de eso había que contar como efectivo.
                    FilaResumen("   · Mixto en efectivo", totalMixtoEfectivo)
                    FilaResumen("   · Mixto en transferencia", totalMixtoTransferencia)
                }
                Divider(modifier = Modifier.padding(vertical = 10.dp))
                FilaResumen("Total esperado", apertura + totalEfectivo + totalMixtoEfectivo, destacado = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Productos vendidos", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (productosVendidos.isEmpty()) EstadoVacio("Aún no hay ventas en este turno")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(productosVendidos) { (nombre, cantidad) ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(nombre, style = MaterialTheme.typography.bodyMedium)
                        Text(if (cantidad == cantidad.toLong().toDouble()) cantidad.toLong().toString() else cantidad.toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCerrar, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Cerrar turno") }
    }
}

@Composable
private fun FilaResumen(etiqueta: String, valor: Double, destacado: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text("$valor CUP", style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal, color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AbrirTurnoDialog(isSaving: Boolean, onDismiss: () -> Unit, onAbrir: (Double) -> Unit) {
    var monto by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp), title = { Text("Abrir turno", fontWeight = FontWeight.Bold) }, text = { OutlinedTextField(monto, { monto = it }, label = { Text("Efectivo inicial") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) }, confirmButton = { TextButton(enabled = (monto.toDoubleOrNull() ?: 0.0) >= 0 && !isSaving, onClick = { onAbrir(monto.toDoubleOrNull() ?: 0.0) }) { Text(if (isSaving) "Abriendo..." else "Abrir") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun CerrarTurnoDialog(efectivoEsperado: Double, isSaving: Boolean, onDismiss: () -> Unit, onCerrar: (Double) -> Unit) {
    var monto by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp), title = { Text("Cerrar turno", fontWeight = FontWeight.Bold) }, text = { Column { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Text("Esperado: $efectivoEsperado CUP", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(12.dp)); OutlinedTextField(monto, { monto = it }, label = { Text("Efectivo contado") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) } }, confirmButton = { TextButton(enabled = (monto.toDoubleOrNull() ?: 0.0) >= 0 && !isSaving, onClick = { onCerrar(monto.toDoubleOrNull() ?: 0.0) }) { Text(if (isSaving) "Cerrando..." else "Cerrar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
