package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.Tarjeta
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.ui.viewmodels.SaleViewModel
import org.luisito.gestor360.ui.viewmodels.TarjetaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasScreen(
    androidId: String, onBack: () -> Unit,
    viewModel: SaleViewModel = viewModel(),
    tarjetaViewModel: TarjetaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tarjetaUiState by tarjetaViewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var cantidad by remember { mutableStateOf("") }
    var showEfectivoConfirm by remember { mutableStateOf(false) }
    var showTransferenciaDialog by remember { mutableStateOf(false) }
    var showMixtoDialog by remember { mutableStateOf(false) }
    var showMixtoTransferencia by remember { mutableStateOf(false) }
    var montoEfectivoMixto by remember { mutableStateOf(0.0) }
    var clienteCi by remember { mutableStateOf("") }
    var clienteTel by remember { mutableStateOf("") }
    var clienteNombre by remember { mutableStateOf("") }
    var tarjetaSeleccionada by remember { mutableStateOf<Tarjeta?>(null) }
    var menuTarjetasAbierto by remember { mutableStateOf(false) }

    LaunchedEffect(androidId) { viewModel.iniciar(androidId); tarjetaViewModel.cargar(androidId, "") }

    val productosFiltrados = uiState.productos.filter { searchQuery.isBlank() || it.nombre.contains(searchQuery, true) }
    val totalCarrito = uiState.carrito.sumOf { it.subtotal }

    Scaffold(topBar = { TopAppBar(title = { Text("Ventas") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(searchQuery, { searchQuery = it }, label = { Text("Buscar producto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(productosFiltrados) { p ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { selectedProduct = p; cantidad = "" }) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(p.nombre, style = MaterialTheme.typography.titleMedium); Text("Stock: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall) }
                            Text("${p.precio} CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (uiState.carrito.isNotEmpty()) {
                Divider()
                Text("Carrito (${uiState.carrito.size} items)", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(uiState.carrito.size) { i -> val item = uiState.carrito[i]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) { Text("${item.nombre} x${item.cantidad.toInt()} = ${item.subtotal} CUP", Modifier.weight(1f)); IconButton(onClick = { viewModel.quitarDelCarrito(i) }) { Icon(Icons.Default.Delete, "Quitar") } }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Total: $totalCarrito CUP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button({ showEfectivoConfirm = true }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary), contentPadding = PaddingValues(6.dp)) { Text("EFECTIVO", style = MaterialTheme.typography.labelSmall) }
                    Button({ showTransferenciaDialog = true }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary), contentPadding = PaddingValues(6.dp)) { Text("TRANSFER", style = MaterialTheme.typography.labelSmall) }
                    Button({ showMixtoDialog = true }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary), contentPadding = PaddingValues(6.dp)) { Text("MIXTO", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }

    if (selectedProduct != null) {
        AlertDialog(onDismissRequest = { selectedProduct = null }, title = { Text(selectedProduct!!.nombre) }, text = { Column { Text("Stock: ${selectedProduct!!.stock.toInt()} unidades"); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(cantidad, { cantidad = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) } }, confirmButton = { TextButton(onClick = { val c = cantidad.toDoubleOrNull() ?: 0.0; val err = viewModel.agregarAlCarrito(selectedProduct!!, c); if (err == null) { selectedProduct = null; cantidad = "" } }) { Text("Agregar") } }, dismissButton = { TextButton(onClick = { selectedProduct = null }) { Text("Cancelar") } })
    }

    if (showEfectivoConfirm) AlertDialog(onDismissRequest = { showEfectivoConfirm = false }, title = { Text("Confirmar venta") }, text = { Text("Total: $totalCarrito CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }, confirmButton = { TextButton(onClick = { viewModel.confirmarVenta("cash", totalCarrito, 0.0, 0L, null); showEfectivoConfirm = false }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = { showEfectivoConfirm = false }) { Text("Cancelar") } })

    if (showTransferenciaDialog) DatosClienteDialog("Transferencia", totalCarrito, tarjetaUiState.tarjetas, { showTransferenciaDialog = false }) { ci, tel, nombre, tarjeta ->
        viewModel.confirmarVenta("transfer", 0.0, totalCarrito, 0L, SaleRepository.DatosCliente(ci, tel, nombre, "${tarjeta.banco} · ${tarjeta.numero}")); showTransferenciaDialog = false
    }

    if (showMixtoDialog) {
        var efTexto by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { showMixtoDialog = false }, title = { Text("Pago mixto") }, text = { Column { Text("Total: $totalCarrito CUP"); OutlinedTextField(efTexto, { efTexto = it.filter { c -> c.isDigit() } }, label = { Text("Monto en efectivo") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); val ef = efTexto.toDoubleOrNull() ?: 0.0; if (ef > 0) Text("Restante: ${totalCarrito - ef} CUP") } }, confirmButton = { val ef = efTexto.toDoubleOrNull() ?: 0.0; TextButton(enabled = ef > 0 && ef < totalCarrito, onClick = { montoEfectivoMixto = ef; showMixtoDialog = false; showMixtoTransferencia = true }) { Text("Continuar") } }, dismissButton = { TextButton(onClick = { showMixtoDialog = false }) { Text("Cancelar") } })
    }

    if (showMixtoTransferencia) DatosClienteDialog("Mixto - Transferencia", totalCarrito - montoEfectivoMixto, tarjetaUiState.tarjetas, { showMixtoTransferencia = false; showMixtoDialog = true }) { ci, tel, nombre, tarjeta ->
        viewModel.confirmarVenta("mixed", montoEfectivoMixto, totalCarrito - montoEfectivoMixto, 0L, SaleRepository.DatosCliente(ci, tel, nombre, "${tarjeta.banco} · ${tarjeta.numero}")); showMixtoTransferencia = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatosClienteDialog(titulo: String, monto: Double, tarjetas: List<Tarjeta>, onDismiss: () -> Unit, onConfirmar: (String, String, String, Tarjeta) -> Unit) {
    var ci by remember { mutableStateOf("") }; var tel by remember { mutableStateOf("") }; var nombre by remember { mutableStateOf("") }
    var tarjetaSel by remember { mutableStateOf(tarjetas.firstOrNull()) }; var menuAbierto by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(titulo) }, text = { Column {
        Text("Monto: $monto CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        if (tarjetas.isEmpty()) Text("No hay tarjetas", color = MaterialTheme.colorScheme.error) else ExposedDropdownMenuBox(menuAbierto, { menuAbierto = it }) {
            OutlinedTextField(tarjetaSel?.let { "${it.banco} · ${it.numero}" } ?: "Seleccionar", {}, readOnly = true, label = { Text("Cuenta destino") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuAbierto) }, modifier = Modifier.fillMaxWidth().menuAnchor())
            ExposedDropdownMenu(menuAbierto, { menuAbierto = false }) { tarjetas.forEach { t -> DropdownMenuItem(text = { Text("${t.banco} · ${t.numero}") }, onClick = { tarjetaSel = t; menuAbierto = false }) } }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(ci, { ci = it.filter { c -> c.isDigit() } }, label = { Text("CI") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(tel, { tel = it.filter { c -> c.isDigit() } }, label = { Text("Teléfono") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nombre, { nombre = it.uppercase() }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    } }, confirmButton = { TextButton(enabled = ci.isNotBlank() && tel.isNotBlank() && nombre.isNotBlank() && tarjetaSel != null, onClick = { onConfirmar(ci, tel, nombre, tarjetaSel!!) }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
