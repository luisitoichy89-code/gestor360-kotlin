package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.ui.components.EstadoCargando
import org.luisito.gestor360.ui.components.EstadoError
import org.luisito.gestor360.ui.components.EstadoVacio
import org.luisito.gestor360.ui.viewmodels.AprobacionStockViewModel
import org.luisito.gestor360.ui.viewmodels.CierreCajaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CierreCajaScreen(
    androidId: String, onBack: (() -> Unit)? = null,
    viewModel: CierreCajaViewModel = viewModel(),
    aprobacionVM: AprobacionStockViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarAbrirTurno by remember { mutableStateOf(false) }
    var mostrarCerrarTurno by remember { mutableStateOf(false) }
    var ventaAAnular by remember { mutableStateOf<org.luisito.gestor360.data.models.Sale?>(null) }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(topBar = { TopAppBar(title = { Text("Cierre de caja") }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }, actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, "Refrescar") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                uiState.turnoActivo == null -> TurnoCerradoPanel(isSaving = uiState.isSaving, onAbrir = { mostrarAbrirTurno = true })
                else -> TurnoAbiertoPanel(
                    apertura = uiState.turnoActivo!!.apertura,
                    productosVendidos = uiState.productosVendidos,
                    ventas = uiState.ventasDelTurno,
                    totalEfectivo = uiState.totalEfectivo,
                    totalTransferencia = uiState.totalTransferencia,
                    totalMixto = uiState.totalMixto,
                    onCerrar = { mostrarCerrarTurno = true },
                    onAnular = { ventaAAnular = it }
                )
            }
        }
    }

    if (mostrarAbrirTurno) AbrirTurnoDialog(uiState.isSaving, { mostrarAbrirTurno = false }, { viewModel.abrirTurno(it); mostrarAbrirTurno = false })
    if (mostrarCerrarTurno) CerrarTurnoDialog((uiState.turnoActivo?.apertura ?: 0.0) + uiState.totalEfectivo, uiState.isSaving, { mostrarCerrarTurno = false }, { viewModel.cerrarTurno(it); mostrarCerrarTurno = false })

    uiState.turnoRecienCerrado?.let { turno ->
        val dif = turno.diferencia ?: 0.0
        AlertDialog(onDismissRequest = { viewModel.limpiarTurnoRecienCerrado() }, title = { Text("Turno cerrado") }, text = { Column { Text("Efectivo contado: ${turno.cierre} CUP"); Text(when { dif > 0 -> "Sobran $dif CUP"; dif < 0 -> "Faltan ${-dif} CUP"; else -> "Cuadra exacto ✅" }, fontWeight = FontWeight.Bold, color = when { dif > 0 -> MaterialTheme.colorScheme.tertiary; dif < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }) } }, confirmButton = { TextButton(onClick = { viewModel.limpiarTurnoRecienCerrado() }) { Text("Entendido") } })
    }

    if (ventaAAnular != null) {
        AlertDialog(onDismissRequest = { ventaAAnular = null }, title = { Text("Solicitar anulación") }, text = { Text("¿Anular venta de ${ventaAAnular!!.total} CUP? Esto debe ser aprobado por el admin.") }, confirmButton = { TextButton(onClick = { aprobacionVM.solicitarAnularVenta(androidId, ventaAAnular!!.id ?: "", ventaAAnular!!.total); ventaAAnular = null }) { Text("Enviar a aprobación") } }, dismissButton = { TextButton(onClick = { ventaAAnular = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun TurnoCerradoPanel(isSaving: Boolean, onAbrir: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LockOpen, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("No tienes un turno abierto", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAbrir, enabled = !isSaving) { Text("Abrir turno") }
    }
}

@Composable
private fun TurnoAbiertoPanel(apertura: Double, productosVendidos: List<Pair<String, Double>>, ventas: List<org.luisito.gestor360.data.models.Sale>, totalEfectivo: Double, totalTransferencia: Double, totalMixto: Double, onCerrar: () -> Unit, onAnular: (org.luisito.gestor360.data.models.Sale) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                FilaResumen("Apertura", apertura)
                FilaResumen("Efectivo vendido", totalEfectivo)
                FilaResumen("Transferencia", totalTransferencia)
                FilaResumen("Mixto", totalMixto)
                Divider(Modifier.padding(vertical = 8.dp))
                FilaResumen("Total en caja esperado", apertura + totalEfectivo, destacado = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Ventas del turno", style = MaterialTheme.typography.labelLarge)
        if (ventas.isEmpty()) EstadoVacio("Aún no hay ventas en este turno")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ventas) { venta ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Producto #${venta.producto_id}", fontWeight = FontWeight.Bold)
                        Text("${venta.total} CUP · ${venta.metodo} · ${venta.created_at?.take(16)?.replace("T", " ") ?: ""}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { onAnular(venta) }) { Icon(Icons.Default.Block, "Anular", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCerrar, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cerrar turno") }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Abrir turno") }, text = { OutlinedTextField(monto, { monto = it.filter { c -> c.isDigit() } }, label = { Text("Efectivo inicial") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(enabled = (monto.toDoubleOrNull() ?: 0.0) >= 0 && !isSaving, onClick = { onAbrir(monto.toDoubleOrNull() ?: 0.0) }) { Text(if (isSaving) "Abriendo..." else "Abrir") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun CerrarTurnoDialog(efectivoEsperado: Double, isSaving: Boolean, onDismiss: () -> Unit, onCerrar: (Double) -> Unit) {
    var monto by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Cerrar turno") }, text = { Column { Text("Efectivo esperado: $efectivoEsperado CUP"); Spacer(Modifier.height(12.dp)); OutlinedTextField(monto, { monto = it.filter { c -> c.isDigit() } }, label = { Text("Efectivo contado (real)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) } }, confirmButton = { TextButton(enabled = (monto.toDoubleOrNull() ?: 0.0) >= 0 && !isSaving, onClick = { onCerrar(monto.toDoubleOrNull() ?: 0.0) }) { Text(if (isSaving) "Cerrando..." else "Cerrar turno") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
