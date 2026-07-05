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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.ui.viewmodels.SaleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasScreen(
    androidId: String,
    onBack: () -> Unit,
    viewModel: SaleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var cantidad by remember { mutableStateOf(1.0) }
    var showMetodoDialog by remember { mutableStateOf(false) }
    var metodoSeleccionado by remember { mutableStateOf("") }
    var clienteCi by remember { mutableStateOf("") }
    var clienteTel by remember { mutableStateOf("") }
    var clienteNombre by remember { mutableStateOf("") }

    LaunchedEffect(androidId) { viewModel.iniciar(androidId) }

    val productosFiltrados = uiState.productos.filter {
        searchQuery.isBlank() || it.nombre.contains(searchQuery, true)
    }

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
                            Column {
                                Text(producto.nombre, style = MaterialTheme.typography.titleMedium)
                                Text("Stock: ${producto.stock} | ${producto.precio} CUP", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.Add, "Agregar")
                        }
                    }
                }
            }

            if (uiState.carrito.isNotEmpty()) {
                Divider()
                Text("Carrito (${uiState.carrito.size} items)", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                uiState.carrito.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.nombre} x${item.cantidad} = ${item.subtotal} CUP", modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.quitarDelCarrito(index) }) { Icon(Icons.Default.Delete, "Quitar") }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val total = uiState.carrito.sumOf { it.subtotal }
                Text("Total: $total CUP", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { metodoSeleccionado = "cash"; showMetodoDialog = true }, modifier = Modifier.weight(1f)) { Text("💵 Efectivo") }
                    Button(onClick = { metodoSeleccionado = "transfer"; showMetodoDialog = true }, modifier = Modifier.weight(1f)) { Text("📲 Transfer.") }
                }
            }
        }
    }

    if (selectedProduct != null) {
        AlertDialog(
            onDismissRequest = { selectedProduct = null },
            title = { Text(selectedProduct!!.nombre) },
            text = {
                Column {
                    Text("Stock: ${selectedProduct!!.stock}")
                    OutlinedTextField(value = cantidad.toString(), onValueChange = { cantidad = it.toDoubleOrNull() ?: 1.0 }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.agregarAlCarrito(selectedProduct!!, cantidad); selectedProduct = null }) { Text("Agregar") } },
            dismissButton = { TextButton(onClick = { selectedProduct = null }) { Text("Cancelar") } }
        )
    }

    if (showMetodoDialog) {
        AlertDialog(
            onDismissRequest = { showMetodoDialog = false },
            title = { Text("Datos del cliente") },
            text = {
                Column {
                    OutlinedTextField(value = clienteCi, onValueChange = { clienteCi = it }, label = { Text("CI") })
                    OutlinedTextField(value = clienteTel, onValueChange = { clienteTel = it }, label = { Text("Teléfono") })
                    OutlinedTextField(value = clienteNombre, onValueChange = { clienteNombre = it }, label = { Text("Nombre") })
                    if (metodoSeleccionado == "cash") {
                        Text("Total a pagar: ${uiState.carrito.sumOf { it.subtotal }} CUP")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cliente = SaleRepository.DatosCliente(clienteCi, clienteTel, clienteNombre, "")
                    viewModel.confirmarVenta(metodoSeleccionado, 0.0, 0.0, 0L, cliente)
                    showMetodoDialog = false
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { showMetodoDialog = false }) { Text("Cancelar") } }
        )
    }
}
