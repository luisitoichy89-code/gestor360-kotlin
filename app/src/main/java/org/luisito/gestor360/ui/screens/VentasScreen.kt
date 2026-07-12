package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.ui.viewmodels.SaleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasScreen(
    androidId: String,
    onBack: () -> Unit,
    onIrACarrito: () -> Unit,
    viewModel: SaleViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var cantidad by remember { mutableStateOf("") }
    var mostrarCancelarVenta by remember { mutableStateOf(false) }

    LaunchedEffect(androidId) { viewModel.iniciar(androidId) }

    val productosFiltrados = uiState.productos.filter { searchQuery.isBlank() || it.nombre.contains(searchQuery, true) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ventas", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    if (uiState.carrito.isNotEmpty()) {
                        IconButton(onClick = { mostrarCancelarVenta = true }) {
                            Icon(Icons.Default.Cancel, "Cancelar venta", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.carrito.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = onIrACarrito, icon = { Icon(Icons.Default.ShoppingCart, null) }, text = { Text("Carrito (${uiState.carrito.size}) · ${uiState.totalCarrito} CUP") })
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            OutlinedTextField(
                searchQuery, { searchQuery = it },
                label = { Text("Buscar producto") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(18.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(productosFiltrados) { p ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 5.dp),
                        onClick = { selectedProduct = p; cantidad = "" }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("Stock: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) {
                                Text("${p.precio} CUP", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedProduct != null) {
        AlertDialog(
            onDismissRequest = { selectedProduct = null },
            title = { Text(selectedProduct!!.nombre, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Stock disponible: ${selectedProduct!!.stock.toInt()}")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        cantidad, { cantidad = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { selectedProduct = null },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Cancelar", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = {
                                val c = cantidad.toDoubleOrNull() ?: 0.0
                                val err = viewModel.agregarAlCarrito(selectedProduct!!, c)
                                if (err == null) { selectedProduct = null; cantidad = "" }
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Agregar", fontWeight = FontWeight.Bold) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (mostrarCancelarVenta) {
        AlertDialog(
            onDismissRequest = { mostrarCancelarVenta = false },
            title = { Text("Cancelar venta", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de cancelar la venta? Se vaciará el carrito.") },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { mostrarCancelarVenta = false },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Volver", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            viewModel.limpiarCarrito()
                            mostrarCancelarVenta = false
                            onBack()
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Cancelar venta", fontWeight = FontWeight.Bold) }
                }
            }
        )
    }
}
