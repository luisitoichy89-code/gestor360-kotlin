package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.models.Tarjeta
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
    var showMixtoResumen by remember { mutableStateOf(false) }
    var montoEfectivoMixto by remember { mutableStateOf(0.0) }
    var mixtoClienteCi by remember { mutableStateOf("") }
    var mixtoClienteTel by remember { mutableStateOf("") }
    var mixtoClienteNombre by remember { mutableStateOf("") }
    var mixtoTarjeta by remember { mutableStateOf<Tarjeta?>(null) }

    LaunchedEffect(androidId) { viewModel.iniciar(androidId); tarjetaViewModel.cargar(androidId) }

    val productosFiltrados = uiState.productos.filter { searchQuery.isBlank() || it.nombre.contains(searchQuery, true) }
    val totalCarrito = uiState.carrito.sumOf { it.subtotal }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Text("Ventas", fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            OutlinedTextField(searchQuery, { searchQuery = it }, label = { Text("Buscar producto") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(18.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(productosFiltrados) { p ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 5.dp), onClick = { selectedProduct = p; cantidad = "" }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("Stock: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) { Text("${p.precio} CUP", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
            if (uiState.carrito.isNotEmpty()) {
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("Carrito (${uiState.carrito.size} productos)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(uiState.carrito.size) { i -> val item = uiState.carrito[i]
                        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(item.nombre, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(2.dp)); Text("${item.cantidad.toInt()} × ${item.subtotal} CUP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                FilledIconButton(onClick = { viewModel.quitarDelCarrito(i) }) { Icon(Icons.Default.Delete, "Eliminar") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("TOTAL", fontWeight = FontWeight.Bold); Text("$totalCarrito CUP", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary) } }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ showEfectivoConfirm = true }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("EFECTIVO") }; Button({ showTransferenciaDialog = true }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("TRANSFER") }; Button({ showMixtoDialog = true }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("MIXTO") } }
            }
        }
    }

    if (selectedProduct != null) AlertDialog(onDismissRequest = { selectedProduct = null }, title = { Text(selectedProduct!!.nombre, fontWeight = FontWeight.Bold) }, text = { Column { Text("Stock disponible: ${selectedProduct!!.stock.toInt()}"); Spacer(Modifier.height(12.dp)); OutlinedTextField(cantidad, { cantidad = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Cantidad") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(14.dp)) } }, confirmButton = { TextButton(onClick = { val c = cantidad.toDoubleOrNull() ?: 0.0; val err = viewModel.agregarAlCarrito(selectedProduct!!, c); if (err == null) { selectedProduct = null; cantidad = "" } }) { Text("Agregar", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { selectedProduct = null }) { Text("Cancelar") } })

    if (showEfectivoConfirm) AlertDialog(onDismissRequest = { showEfectivoConfirm = false }, title = { Text("Confirmar venta", fontWeight = FontWeight.Bold) }, text = { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp)) { Text("Total a cobrar", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(6.dp)); Text("$totalCarrito CUP", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold) } } }, confirmButton = { TextButton(onClick = { viewModel.confirmarVenta("cash", totalCarrito, 0.0, 0L, null); showEfectivoConfirm = false }) { Text("Aceptar", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showEfectivoConfirm = false }) { Text("Cancelar") } })

    if (showTransferenciaDialog) DatosClienteDialog("Transferencia", totalCarrito, tarjetaUiState.tarjetas, { showTransferenciaDialog = false }) { ci, tel, nombre, tarjeta -> viewModel.confirmarVenta("transfer", 0.0, totalCarrito, 0L, SaleRepository.DatosCliente(ci, tel, nombre, "${tarjeta.banco} · ${tarjeta.numero}")); showTransferenciaDialog = false }

    if (showMixtoDialog) { var efTexto by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = { showMixtoDialog = false }, title = { Text("Pago mixto", fontWeight = FontWeight.Bold) }, text = { Column { Text("Total: $totalCarrito CUP"); Spacer(Modifier.height(12.dp)); OutlinedTextField(efTexto, { efTexto = it.filter { c -> c.isDigit() } }, label = { Text("Monto en efectivo") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(14.dp)); val ef = efTexto.toDoubleOrNull() ?: 0.0; if (ef > 0) { Spacer(Modifier.height(8.dp)); Text("Restante: ${totalCarrito - ef} CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }, confirmButton = { val ef = efTexto.toDoubleOrNull() ?: 0.0; TextButton(enabled = ef > 0 && ef < totalCarrito, onClick = { montoEfectivoMixto = ef; showMixtoDialog = false; showMixtoTransferencia = true }) { Text("Continuar", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showMixtoDialog = false }) { Text("Cancelar") } }) }

    if (showMixtoTransferencia) { val restante = totalCarrito - montoEfectivoMixto; DatosClienteDialog("Mixto - Transferencia", restante, tarjetaUiState.tarjetas, { showMixtoTransferencia = false; showMixtoDialog = true }) { ci, tel, nombre, tarjeta -> mixtoClienteCi = ci; mixtoClienteTel = tel; mixtoClienteNombre = nombre; mixtoTarjeta = tarjeta; showMixtoTransferencia = false; showMixtoResumen = true } }

    if (showMixtoResumen) { val restante = totalCarrito - montoEfectivoMixto; AlertDialog(onDismissRequest = { showMixtoResumen = false }, title = { Text("Confirmar pago mixto", fontWeight = FontWeight.Bold) }, text = { Column { Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(14.dp)) { Text("💵 Efectivo: $montoEfectivoMixto CUP", fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("📲 Transferencia: $restante CUP", fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("💰 Total: $totalCarrito CUP", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary) } }; Spacer(Modifier.height(12.dp)); if (mixtoTarjeta != null) Text("Cuenta: ${mixtoTarjeta!!.banco} · ${mixtoTarjeta!!.numero}"); Text("Cliente: $mixtoClienteNombre"); Text("CI: $mixtoClienteCi"); Text("Teléfono: $mixtoClienteTel") } }, confirmButton = { TextButton(onClick = { viewModel.confirmarVenta("mixed", montoEfectivoMixto, restante, 0L, SaleRepository.DatosCliente(mixtoClienteCi, mixtoClienteTel, mixtoClienteNombre, "${mixtoTarjeta!!.banco} · ${mixtoTarjeta!!.numero}")); showMixtoResumen = false }) { Text("Confirmar", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showMixtoResumen = false; showMixtoDialog = true }) { Text("Cancelar") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatosClienteDialog(titulo: String, monto: Double, tarjetas: List<Tarjeta>, onDismiss: () -> Unit, onConfirmar: (String, String, String, Tarjeta) -> Unit) {
    var ci by remember { mutableStateOf("") }; var tel by remember { mutableStateOf("") }; var nombre by remember { mutableStateOf("") }
    var tarjetaSel by remember { mutableStateOf(tarjetas.firstOrNull()) }; var menuAbierto by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(titulo, fontWeight = FontWeight.Bold) }, text = { Column {
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(14.dp)) { Text("Monto", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(6.dp)); Text("$monto CUP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary) } }
        Spacer(Modifier.height(12.dp))
        if (tarjetas.isEmpty()) Text("No hay tarjetas disponibles", color = MaterialTheme.colorScheme.error) else ExposedDropdownMenuBox(menuAbierto, { menuAbierto = it }) { OutlinedTextField(tarjetaSel?.let { "${it.banco} · ${it.numero}" } ?: "", {}, readOnly = true, label = { Text("Cuenta destino") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuAbierto) }, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp)); ExposedDropdownMenu(menuAbierto, { menuAbierto = false }) { tarjetas.forEach { t -> DropdownMenuItem(text = { Text("${t.banco} · ${t.numero}") }, onClick = { tarjetaSel = t; menuAbierto = false }) } } }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(ci, { ci = it.filter { c -> c.isDigit() } }, label = { Text("CI") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(tel, { tel = it.filter { c -> c.isDigit() } }, label = { Text("Teléfono") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(nombre, { nombre = it.uppercase() }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
    } }, confirmButton = { TextButton(enabled = ci.isNotBlank() && tel.isNotBlank() && nombre.isNotBlank() && tarjetaSel != null, onClick = { onConfirmar(ci, tel, nombre, tarjetaSel!!) }) { Text("Confirmar", fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
