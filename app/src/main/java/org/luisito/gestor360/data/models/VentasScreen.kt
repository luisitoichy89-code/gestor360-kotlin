package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.CartItem
import org.luisito.gestor360.data.models.MetodoPago
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.models.Tarjeta
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.data.repository.TopVendido
import org.luisito.gestor360.ui.viewmodels.SaleViewModel
import org.luisito.gestor360.ui.viewmodels.TarjetaViewModel
import kotlin.math.max

/**
 * Pantalla de ventas tipo POS: buscar producto, agregarlo con cantidad al carrito y cobrar
 * en efectivo, transferencia o mixto. Equivalente a la ruta /ventas del backend Flask.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasScreen(
    androidId: String,
    onBack: (() -> Unit)? = null,
    viewModel: SaleViewModel = viewModel(),
    tarjetaViewModel: TarjetaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tarjetaUiState by tarjetaViewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var productoSeleccionado by remember { mutableStateOf<Product?>(null) }
    var mostrarTransferencia by remember { mutableStateOf(false) }
    var mostrarMixto by remember { mutableStateOf(false) }
    var montoEfectivoMixto by remember { mutableStateOf(0.0) }

    LaunchedEffect(androidId) { viewModel.iniciar(androidId) }
    LaunchedEffect(androidId) { tarjetaViewModel.cargar(androidId) }

    LaunchedEffect(uiState.ventaConfirmada) {
        if (uiState.ventaConfirmada != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.limpiarVentaConfirmada()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ventas") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            uiState.ventaConfirmada?.let { total ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "✅ Venta registrada: $total CUP",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            uiState.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                if (uiState.top5.isNotEmpty() && uiState.carrito.isEmpty()) {
                    Text("🏆 Top 5 más vendidos", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.top5) { top ->
                            Top5Chip(top) {
                                query = top.producto_nombre
                                viewModel.buscarProducto(query)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; viewModel.buscarProducto(it) },
                    label = { Text("Buscar producto...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.resultadosBusqueda.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false).padding(horizontal = 16.dp)) {
                    items(uiState.resultadosBusqueda, key = { it.id }) { producto ->
                        ProductoResultadoItem(producto) {
                            productoSeleccionado = producto
                            query = ""
                            viewModel.limpiarBusqueda()
                        }
                    }
                }
            }

            productoSeleccionado?.let { producto ->
                SelectorCantidadCard(
                    producto = producto,
                    onCancelar = { productoSeleccionado = null },
                    onAgregar = { cantidad ->
                        val error = viewModel.agregarAlCarrito(producto, cantidad)
                        if (error == null) productoSeleccionado = null
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.carrito) { index, item ->
                    CarritoItemRow(item) { viewModel.quitarDelCarrito(index) }
                }
            }

            if (uiState.carrito.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total de la venta", style = MaterialTheme.typography.bodyMedium)
                            Text("${uiState.total} CUP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.confirmarVenta(MetodoPago.EFECTIVO.valor, uiState.total, 0.0) },
                                enabled = !uiState.isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Efectivo")
                            }
                            OutlinedButton(
                                onClick = { mostrarTransferencia = true },
                                enabled = !uiState.isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Transf.")
                            }
                            OutlinedButton(
                                onClick = { mostrarMixto = true },
                                enabled = !uiState.isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Percent, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mixto")
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarTransferencia) {
        DatosClienteDialog(
            titulo = "Transferencia",
            montoATransferir = uiState.total,
            isSaving = uiState.isSaving,
            tarjetas = tarjetaUiState.tarjetas.filter { it.activo },
            onDismiss = { mostrarTransferencia = false },
            onConfirmar = { cliente ->
                viewModel.confirmarVenta(MetodoPago.TRANSFERENCIA.valor, 0.0, uiState.total, cliente)
                mostrarTransferencia = false
            }
        )
    }

    if (mostrarMixto) {
        MontoEfectivoDialog(
            total = uiState.total,
            onDismiss = { mostrarMixto = false },
            onContinuar = { efectivo ->
                montoEfectivoMixto = efectivo
                mostrarMixto = false
                mostrarTransferencia = true
            }
        )
    }

    // Si el monto mixto ya fue definido, el diálogo de transferencia siguiente confirma con "mixed".
    if (mostrarTransferencia && montoEfectivoMixto > 0.0) {
        DatosClienteDialog(
            titulo = "Pago mixto",
            montoATransferir = max(0.0, uiState.total - montoEfectivoMixto),
            isSaving = uiState.isSaving,
            tarjetas = tarjetaUiState.tarjetas.filter { it.activo },
            onDismiss = { mostrarTransferencia = false; montoEfectivoMixto = 0.0 },
            onConfirmar = { cliente ->
                viewModel.confirmarVenta(
                    MetodoPago.MIXTO.valor,
                    montoEfectivoMixto,
                    max(0.0, uiState.total - montoEfectivoMixto),
                    cliente
                )
                mostrarTransferencia = false
                montoEfectivoMixto = 0.0
            }
        )
    }
}

@Composable
private fun Top5Chip(top: TopVendido, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(top.producto_nombre, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text("${top.total.toInt()} vendidos", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ProductoResultadoItem(producto: Product, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(producto.nombre, fontWeight = FontWeight.Bold)
                Text("Stock: ${producto.stock}", style = MaterialTheme.typography.bodySmall)
            }
            Text("${producto.precio} CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SelectorCantidadCard(producto: Product, onCancelar: () -> Unit, onAgregar: (Double) -> Unit) {
    var cantidad by remember(producto.id) { mutableStateOf(1.0) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(producto.nombre, style = MaterialTheme.typography.titleMedium)
                    Text("${producto.precio} CUP · Disponible: ${producto.stock}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onCancelar) { Icon(Icons.Default.Close, contentDescription = "Cancelar") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { if (cantidad > 1) cantidad -= 1 }) { Icon(Icons.Default.Remove, contentDescription = null) }
                OutlinedTextField(
                    value = if (cantidad == cantidad.toLong().toDouble()) cantidad.toLong().toString() else cantidad.toString(),
                    onValueChange = { it.toDoubleOrNull()?.let { v -> if (v in 0.0..producto.stock) cantidad = v } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                IconButton(onClick = { if (cantidad < producto.stock) cantidad += 1 }) { Icon(Icons.Default.Add, contentDescription = null) }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onAgregar(cantidad) },
                enabled = cantidad > 0 && cantidad <= producto.stock,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Agregar al carrito")
            }
        }
    }
}

@Composable
private fun CarritoItemRow(item: CartItem, onEliminar: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(item.nombre, fontWeight = FontWeight.Bold)
                Text("${item.cantidad} × ${item.precio} CUP", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${item.subtotal} CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                IconButton(onClick = onEliminar) { Icon(Icons.Default.Close, contentDescription = "Quitar") }
            }
        }
    }
}

@Composable
private fun MontoEfectivoDialog(total: Double, onDismiss: () -> Unit, onContinuar: (Double) -> Unit) {
    var montoTexto by remember { mutableStateOf("") }
    val monto = montoTexto.toDoubleOrNull()
    val valido = monto != null && monto >= 0 && monto <= total

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pago mixto") },
        text = {
            Column {
                Text("Total: $total CUP")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = montoTexto,
                    onValueChange = { montoTexto = it },
                    label = { Text("Monto en efectivo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (monto != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Restante por transferencia: ${max(0.0, total - monto)} CUP", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valido, onClick = { onContinuar(monto ?: 0.0) }) { Text("Continuar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatosClienteDialog(
    titulo: String,
    montoATransferir: Double,
    isSaving: Boolean,
    tarjetas: List<Tarjeta>,
    onDismiss: () -> Unit,
    onConfirmar: (SaleRepository.DatosCliente) -> Unit
) {
    var ci by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var tarjetaSeleccionada by remember { mutableStateOf<Tarjeta?>(tarjetas.firstOrNull()) }
    var menuTarjetasAbierto by remember { mutableStateOf(false) }

    val valido = ci.isNotBlank() && telefono.isNotBlank() && nombre.isNotBlank() && tarjetaSeleccionada != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = {
            Column {
                Text("Monto a transferir: $montoATransferir CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                if (tarjetas.isEmpty()) {
                    Text(
                        "El admin aún no ha agregado ninguna cuenta en Tarjetas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    ExposedDropdownMenuBox(expanded = menuTarjetasAbierto, onExpandedChange = { menuTarjetasAbierto = it }) {
                        OutlinedTextField(
                            value = tarjetaSeleccionada?.let { "${it.banco} · ${it.numero}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
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
                OutlinedTextField(
                    value = ci,
                    onValueChange = { ci = it.filter { c -> c.isDigit() } },
                    label = { Text("CI del cliente") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it.filter { c -> c.isDigit() } },
                    label = { Text("Teléfono") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it.uppercase() },
                    label = { Text("Nombre del cliente") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido && !isSaving,
                onClick = {
                    val tarjeta = tarjetaSeleccionada!!
                    onConfirmar(SaleRepository.DatosCliente(ci, telefono, nombre, "${tarjeta.banco} · ${tarjeta.numero}"))
                }
            ) { Text(if (isSaving) "Guardando..." else "Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
