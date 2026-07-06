package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import org.luisito.gestor360.ui.viewmodels.AprobacionStockViewModel
import org.luisito.gestor360.ui.viewmodels.CierreCajaViewModel
import org.luisito.gestor360.utils.CsvExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CierreCajaScreen(
    androidId: String, onBack: (() -> Unit)? = null,
    viewModel: CierreCajaViewModel = viewModel(),
    aprobacionVM: AprobacionStockViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var mostrarAbrirTurno by remember { mutableStateOf(false) }
    var mostrarCerrarTurno by remember { mutableStateOf(false) }
    var mostrarCancelarVenta by remember { mutableStateOf(false) }
    var ventaSeleccionada by remember { mutableStateOf<org.luisito.gestor360.data.models.Sale?>(null) }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(topBar = { TopAppBar(title = { Text("Cierre de caja") }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }, actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, "Refrescar") } if (uiState.turnoActivo != null) IconButton(onClick = { CsvExporter.exportarCierreCaja(context, uiState.turnoActivo?.created_at?.take(10) ?: "", uiState.productosVendidos, uiState.totalEfectivo, uiState.totalTransferencia, uiState.totalMixto) }) { Icon(Icons.Default.FileDownload, "Exportar CSV") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                uiState.turnoActivo == null -> TurnoCerradoPanel(uiState.isSaving) { mostrarAbrirTurno = true }
                else -> TurnoAbiertoPanel(uiState, { mostrarCerrarTurno = true }, { mostrarCancelarVenta = true })
            }
        }
    }

    if (mostrarAbrirTurno) AbrirTurnoDialog(uiState.isSaving, { mostrarAbrirTurno = false }) { viewModel.abrirTurno(it); mostrarAbrirTurno = false }
    if (mostrarCerrarTurno) CerrarTurnoDialog((uiState.turnoActivo?.apertura ?: 0.0) + uiState.totalEfectivo, uiState.isSaving, { mostrarCerrarTurno = false }) { viewModel.cerrarTurno(it); mostrarCerrarTurno = false }

    if (mostrarCancelarVenta) {
        CancelarVentaDialog(uiState.ventasDelTurno, { mostrarCancelarVenta = false }) { venta ->
            ventaSeleccionada = venta
            mostrarCancelarVenta = false
        }
    }

    if (ventaSeleccionada != null) {
        val venta = ventaSeleccionada!!
        val esAdmin = true // Debes pasar el rol desde el ViewModel o Session. Por ahora asumo admin.
        AlertDialog(
            onDismissRequest = { ventaSeleccionada = null },
            title = { Text(if (esAdmin) "Anular venta" else "Solicitar anulación") },
            text = {
                Column {
                    Text("Producto #${venta.producto_id}")
                    Text("Total: ${venta.total} CUP · ${venta.metodo}")
                    if (!venta.cliente_nombre.isNullOrBlank()) Text("Cliente: ${venta.cliente_nombre}")
                    if (esAdmin) Text("Se anulará inmediatamente y se devolverá el stock.") else Text("Se enviará a aprobación del admin.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (esAdmin) {
                        viewModel.anularVenta(venta.id ?: "")
                    } else {
                        aprobacionVM.solicitarAnularVenta(androidId, venta.id ?: "", venta.total)
                    }
                    ventaSeleccionada = null
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { ventaSeleccionada = null }) { Text("Cancelar") } }
        )
    }

    uiState.turnoRecienCerrado?.let { turno ->
        val dif = turno.diferencia ?: 0.0
        AlertDialog(onDismissRequest = { viewModel.limpiarTurnoRecienCerrado() }, title = { Text("Turno cerrado") }, text = { Column { Text("Efectivo contado: ${turno.cierre} CUP"); Text(when { dif > 0 -> "Sobran $dif CUP"; dif < 0 -> "Faltan ${-dif} CUP"; else -> "Cuadra exacto ✅" }, fontWeight = FontWeight.Bold, color = when { dif > 0 -> MaterialTheme.colorScheme.tertiary; dif < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }) } }, confirmButton = { TextButton(onClick = { viewModel.limpiarTurnoRecienCerrado() }) { Text("Entendido") } })
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
private fun TurnoAbiertoPanel(uiState: CierreCajaUiState, onCerrar: () -> Unit, onCancelarVenta: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                FilaResumen("Apertura", uiState.turnoActivo?.apertura ?: 0.0)
                FilaResumen("Efectivo vendido", uiState.totalEfectivo)
                FilaResumen("Transferencia", uiState.totalTransferencia)
                FilaResumen("Mixto", uiState.totalMixto)
                Divider(Modifier.padding(vertical = 8.dp))
                FilaResumen("Total en caja esperado", (uiState.turnoActivo?.apertura ?: 0.0) + uiState.totalEfectivo, destacado = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Productos vendidos en este turno", style = MaterialTheme.typography.labelLarge)
        if (uiState.productosVendidos.isEmpty()) EstadoVacio("Aún no hay ventas en este turno")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(uiState.productosVendidos) { (nombre, cantidad) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(nombre); Text(if (cantidad == cantidad.toLong().toDouble()) cantidad.toLong().toString() else cantidad.toString(), fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancelarVenta, Modifier.fillMaxWidth()) { Icon(Icons.Default.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cancelar una venta") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCerrar, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cerrar turno") }
    }
}

@Composable
private fun CancelarVentaDialog(ventas: List<org.luisito.gestor360.data.models.Sale>, onDismiss: () -> Unit, onSeleccionar: (org.luisito.gestor360.data.models.Sale) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Selecciona la venta a cancelar") },
        text = {
            if (ventas.isEmpty()) Text("No hay ventas en este turno")
            else LazyColumn { items(ventas) { v ->
                Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), onClick = { onSeleccionar(v) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total: ${v.total} CUP", fontWeight = FontWeight.Bold); Text(v.metodo) }
                        if (!v.cliente_nombre.isNullOrBlank()) Text("Cliente: ${v.cliente_nombre}", style = MaterialTheme.typography.bodySmall)
                        Text(v.created_at?.take(16)?.replace("T", " ") ?: "", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }}
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
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
