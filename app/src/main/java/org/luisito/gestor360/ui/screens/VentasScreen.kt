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
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.models.Tarjeta
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.ui.viewmodels.SaleViewModel
import org.luisito.gestor360.ui.viewmodels.TarjetaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasScreen(
    androidId: String,
    onBack: () -> Unit,
    viewModel: SaleViewModel = viewModel(),
    tarjetaViewModel: TarjetaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tarjetaUiState by tarjetaViewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var cantidad by remember { mutableStateOf(1.0) }
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

    LaunchedEffect(androidId) {
        viewModel.iniciar(androidId)
        tarjetaViewModel.cargar(androidId, "")
    }

    val productosFiltrados = uiState.productos.filter {
        searchQuery.isBlank() || it.nombre.contains(searchQuery, true)
    }

    val totalCarrito = uiState.carrito.sumOf { it.subtotal }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ventas") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Buscar producto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(productosFiltrados) { producto ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { selectedProduct = producto; cantidad = 1.0 }) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(producto.nombre, style = MaterialTheme.typography.titleMedium)
                                Text("Stock: ${producto.stock.toInt()}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${producto.precio} CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (uiState.carrito.isNotEmpty()) {
                Divider()
                Text("Carrito (${uiState.carrito.size} items)", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(uiState.carrito.size) { index ->
                        val item = uiState.carrito[index]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("${item.nombre} x${item.cantidad.toInt()} = ${item.subtotal} CUP", modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.quitarDelCarrito(index) }) { Icon(Icons.Default.Delete, "Quitar") }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Total: $totalCarrito CUP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showEfectivoConfirm = true }, modifier = Modifier.weight(1f)) { Text("💵 Efectivo") }
                    Button(onClick = { showTransferenciaDialog = true }, modifier = Modifier.weight(1f)) { Text("📲 Transfer.") }
                    Button(onClick = { showMixtoDialog = true }, modifier = Modifier.weight(1f)) { Text("💱 Mixto") }
                }
            }
        }
    }

    // Selector de cantidad
    if (selectedProduct != null) {
        AlertDialog(
            onDismissRequest = { selectedProduct = null },
            title = { Text(selectedProduct!!.nombre) },
            text = {
                Column {
                    Text("Stock: ${selectedProduct!!.stock.toInt()} unidades")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = if (cantidad == cantidad.toLong().toDouble()) cantidad.toLong().toString() else cantidad.toString(),
                        onValueChange = { cantidad = it.toDoubleOrNull() ?: 1.0 },
                        label = { Text("Cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.agregarAlCarrito(selectedProduct!!, cantidad); selectedProduct = null }) { Text("Agregar") } },
            dismissButton = { TextButton(onClick = { selectedProduct = null }) { Text("Cancelar") } }
        )
    }

    // Confirmación de efectivo
    if (showEfectivoConfirm) {
        AlertDialog(
            onDismissRequest = { showEfectivoConfirm = false },
            title = { Text("Confirmar venta") },
            text = { Text("Total a cobrar en efectivo:", fontWeight = FontWeight.Bold) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.confirmarVenta("cash", totalCarrito, 0.0, 0L, null)
                    showEfectivoConfirm = false
                }) { Text("Aceptar", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { showEfectivoConfirm = false }) { Text("Cancelar") } }
        )
    }

    // Transferencia
    if (showTransferenciaDialog) {
        DatosClienteDialog(
            titulo = "Transferencia",
            monto = totalCarrito,
            tarjetas = tarjetaUiState.tarjetas,
            onDismiss = { showTransferenciaDialog = false },
            onConfirmar = { ci, tel, nombre, tarjeta ->
                val cliente = SaleRepository.DatosCliente(ci, tel, nombre, "${tarjeta.banco} · ${tarjeta.numero}")
                viewModel.confirmarVenta("transfer", 0.0, totalCarrito, 0L, cliente)
                showTransferenciaDialog = false
            }
        )
    }

    // Mixto - monto efectivo
    if (showMixtoDialog) {
        var efectivoTexto by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMixtoDialog = false },
            title = { Text("Pago mixto") },
            text = {
                Column {
                    Text("Total: $totalCarrito CUP")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = efectivoTexto,
                        onValueChange = { efectivoTexto = it.filter { c -> c.isDigit() } },
                        label = { Text("Monto en efectivo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    val efectivo = efectivoTexto.toDoubleOrNull() ?: 0.0
                    if (efectivo > 0) {
                        Text("Restante transferencia: ${totalCarrito - efectivo} CUP", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                val efectivo = efectivoTexto.toDoubleOrNull() ?: 0.0
                TextButton(enabled = efectivo > 0 && efectivo < totalCarrito, onClick = {
                    montoEfectivoMixto = efectivo
                    showMixtoDialog = false
                    showMixtoTransferencia = true
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { showMixtoDialog = false }) { Text("Cancelar") } }
        )
    }

    // Mixto - transferencia del restante
    if (showMixtoTransferencia) {
        val restante = totalCarrito - montoEfectivoMixto
        DatosClienteDialog(
            titulo = "Mixto - Transferencia",
            monto = restante,
            tarjetas = tarjetaUiState.tarjetas,
            onDismiss = {
                showMixtoTransferencia = false
                showMixtoDialog = true // regresar al paso anterior
            },
            onConfirmar = { ci, tel, nombre, tarjeta ->
                val cliente = SaleRepository.DatosCliente(ci, tel, nombre, "${tarjeta.banco} · ${tarjeta.numero}")
                viewModel.confirmarVenta("mixed", montoEfectivoMixto, restante, 0L, cliente)
                showMixtoTransferencia = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatosClienteDialog(
    titulo: String,
    monto: Double,
    tarjetas: List<Tarjeta>,
    onDismiss: () -> Unit,
    onConfirmar: (String, String, String, Tarjeta) -> Unit
) {
    var ci by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var tarjetaSeleccionada by remember { mutableStateOf(tarjetas.firstOrNull()) }
    var menuTarjetasAbierto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column {
                Text("Monto: $monto CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                if (tarjetas.isEmpty()) {
                    Text("No hay tarjetas registradas", color = MaterialTheme.colorScheme.error)
                } else {
                    ExposedDropdownMenuBox(expanded = menuTarjetasAbierto, onExpandedChange = { menuTarjetasAbierto = it }) {
                        OutlinedTextField(
                            value = tarjetaSeleccionada?.let { "${it.banco} · ${it.numero}" } ?: "Seleccionar tarjeta",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Cuenta destino") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuTarjetasAbierto) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = menuTarjetasAbierto, onDismissRequest = { menuTarjetasAbierto = false }) {
                            tarjetas.forEach { tarjeta ->
                                DropdownMenuItem(
                                    text = { Text("${tarjeta.banco} · ${tarjeta.numero}") },
                                    onClick = { tarjetaSeleccionada = tarjeta; menuTarjetasAbierto = false }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = ci, onValueChange = { ci = it.filter { c -> c.isDigit() } }, label = { Text("CI del cliente") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = telefono, onValueChange = { telefono = it.filter { c -> c.isDigit() } }, label = { Text("Teléfono") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = nombre, onValueChange = { nombre = it.uppercase() }, label = { Text("Nombre del cliente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = ci.isNotBlank() && telefono.isNotBlank() && nombre.isNotBlank() && tarjetaSeleccionada != null,
                onClick = { onConfirmar(ci, telefono, nombre, tarjetaSeleccionada!!) }
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
