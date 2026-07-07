package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.Tarjeta
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.ui.viewmodels.SaleViewModel
import org.luisito.gestor360.ui.viewmodels.TarjetaViewModel

private sealed class PasoCheckout {
    object Ninguno : PasoCheckout()
    object ConfirmarEfectivo : PasoCheckout()
    object DatosTransferencia : PasoCheckout()
    object MontoMixto : PasoCheckout()
    object DatosMixtoTransferencia : PasoCheckout()
    object ResumenMixto : PasoCheckout()
}

private data class DatosMixto(
    val efectivo: Double = 0.0,
    val ci: String = "",
    val tel: String = "",
    val nombre: String = "",
    val tarjeta: Tarjeta? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarritoScreen(
    onBack: () -> Unit,
    onVentaConfirmada: () -> Unit,
    viewModel: SaleViewModel = viewModel(),
    tarjetaViewModel: TarjetaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tarjetaUiState by tarjetaViewModel.uiState.collectAsState()
    var paso by remember { mutableStateOf<PasoCheckout>(PasoCheckout.Ninguno) }
    var datosMixto by remember { mutableStateOf(DatosMixto()) }

    LaunchedEffect(uiState.ventaConfirmada) {
        if (uiState.ventaConfirmada) {
            viewModel.limpiarVentaConfirmada()
            onVentaConfirmada()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Carrito", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            uiState.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearError() }) { Text("Ok") }
                    }
                }
            }

            if (uiState.carrito.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("El carrito está vacío", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("Carrito (${uiState.carrito.size} productos)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uiState.carrito.size) { i ->
                        val item = uiState.carrito[i]
                        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.nombre, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(2.dp))
                                    Text("${item.cantidad.toInt()} × ${item.subtotal} CUP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledIconButton(onClick = { viewModel.quitarDelCarrito(i) }) { Icon(Icons.Default.Delete, "Eliminar") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", fontWeight = FontWeight.Bold)
                        Text("${uiState.totalCarrito} CUP", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ paso = PasoCheckout.ConfirmarEfectivo }, Modifier.weight(1f), enabled = !uiState.isSaving, shape = RoundedCornerShape(14.dp)) { Text("EFECTIVO") }
                    Button({ paso = PasoCheckout.DatosTransferencia }, Modifier.weight(1f), enabled = !uiState.isSaving, shape = RoundedCornerShape(14.dp)) { Text("TRANSFER") }
                    Button({ paso = PasoCheckout.MontoMixto }, Modifier.weight(1f), enabled = !uiState.isSaving, shape = RoundedCornerShape(14.dp)) { Text("MIXTO") }
                }
            }
        }
    }

    when (paso) {
        is PasoCheckout.Ninguno -> {}

        is PasoCheckout.ConfirmarEfectivo -> AlertDialog(
            onDismissRequest = { paso = PasoCheckout.Ninguno },
            title = { Text("Confirmar venta", fontWeight = FontWeight.Bold) },
            text = {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Total a cobrar", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("${uiState.totalCarrito} CUP", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !uiState.isSaving, onClick = {
                    viewModel.confirmarVenta("cash", uiState.totalCarrito, 0.0, 0L, null)
                    paso = PasoCheckout.Ninguno
                }) { Text(if (uiState.isSaving) "Guardando..." else "Aceptar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { paso = PasoCheckout.Ninguno }) { Text("Cancelar") } }
        )

        is PasoCheckout.DatosTransferencia -> DatosClienteDialog(
            titulo = "Transferencia",
            monto = uiState.totalCarrito,
            tarjetas = tarjetaUiState.tarjetas,
            onDismiss = { paso = PasoCheckout.Ninguno }
        ) { ci, tel, nombre, tarjeta ->
            viewModel.confirmarVenta("transfer", 0.0, uiState.totalCarrito, 0L, SaleRepository.DatosCliente(ci, tel, nombre, "${tarjeta.banco} · ${tarjeta.numero}"))
            paso = PasoCheckout.Ninguno
        }

        is PasoCheckout.MontoMixto -> {
            var efTexto by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { paso = PasoCheckout.Ninguno },
                title = { Text("Pago mixto", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Total: ${uiState.totalCarrito} CUP")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            efTexto, { efTexto = it.filter { c -> c.isDigit() } },
                            label = { Text("Monto en efectivo") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, shape = RoundedCornerShape(14.dp)
                        )
                        val ef = efTexto.toDoubleOrNull() ?: 0.0
                        if (ef > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text("Restante: ${uiState.totalCarrito - ef} CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    val ef = efTexto.toDoubleOrNull() ?: 0.0
                    TextButton(enabled = ef > 0 && ef < uiState.totalCarrito, onClick = {
                        datosMixto = datosMixto.copy(efectivo = ef)
                        paso = PasoCheckout.DatosMixtoTransferencia
                    }) { Text("Continuar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { paso = PasoCheckout.Ninguno }) { Text("Cancelar") } }
            )
        }

        is PasoCheckout.DatosMixtoTransferencia -> {
            val restante = uiState.totalCarrito - datosMixto.efectivo
            DatosClienteDialog(
                titulo = "Mixto - Transferencia",
                monto = restante,
                tarjetas = tarjetaUiState.tarjetas,
                onDismiss = { paso = PasoCheckout.MontoMixto }
            ) { ci, tel, nombre, tarjeta ->
                datosMixto = datosMixto.copy(ci = ci, tel = tel, nombre = nombre, tarjeta = tarjeta)
                paso = PasoCheckout.ResumenMixto
            }
        }

        is PasoCheckout.ResumenMixto -> {
            val restante = uiState.totalCarrito - datosMixto.efectivo
            AlertDialog(
                onDismissRequest = { paso = PasoCheckout.Ninguno },
                title = { Text("Confirmar pago mixto", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("💵 Efectivo: ${datosMixto.efectivo} CUP", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("📲 Transferencia: $restante CUP", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("💰 Total: ${uiState.totalCarrito} CUP", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        datosMixto.tarjeta?.let { Text("Cuenta: ${it.banco} · ${it.numero}") }
                        Text("Cliente: ${datosMixto.nombre}")
                        Text("CI: ${datosMixto.ci}")
                        Text("Teléfono: ${datosMixto.tel}")
                    }
                },
                confirmButton = {
                    TextButton(enabled = !uiState.isSaving, onClick = {
                        val tarjeta = datosMixto.tarjeta ?: return@TextButton
                        viewModel.confirmarVenta(
                            "mixed", datosMixto.efectivo, restante, 0L,
                            SaleRepository.DatosCliente(datosMixto.ci, datosMixto.tel, datosMixto.nombre, "${tarjeta.banco} · ${tarjeta.numero}")
                        )
                        paso = PasoCheckout.Ninguno
                        datosMixto = DatosMixto()
                    }) { Text(if (uiState.isSaving) "Guardando..." else "Confirmar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { paso = PasoCheckout.MontoMixto }) { Text("Cancelar") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatosClienteDialog(titulo: String, monto: Double, tarjetas: List<Tarjeta>, onDismiss: () -> Unit, onConfirmar: (String, String, String, Tarjeta) -> Unit) {
    var ci by remember { mutableStateOf("") }
    var tel by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var tarjetaSel by remember { mutableStateOf(tarjetas.firstOrNull()) }
    var menuAbierto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Monto", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("$monto CUP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (tarjetas.isEmpty()) {
                    Text("No hay tarjetas disponibles", color = MaterialTheme.colorScheme.error)
                } else {
                    ExposedDropdownMenuBox(menuAbierto, { menuAbierto = it }) {
                        OutlinedTextField(
                            tarjetaSel?.let { "${it.banco} · ${it.numero}" } ?: "", {},
                            readOnly = true, label = { Text("Cuenta destino") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuAbierto) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp)
                        )
                        ExposedDropdownMenu(menuAbierto, { menuAbierto = false }) {
                            tarjetas.forEach { t -> DropdownMenuItem(text = { Text("${t.banco} · ${t.numero}") }, onClick = { tarjetaSel = t; menuAbierto = false }) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(ci, { ci = it.filter { c -> c.isDigit() } }, label = { Text("CI") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tel, { tel = it.filter { c -> c.isDigit() } }, label = { Text("Teléfono") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = {
            val tarjeta = tarjetaSel
            TextButton(enabled = ci.isNotBlank() && tarjeta != null, onClick = {
                if (tarjeta != null) onConfirmar(ci.trim(), tel.trim(), nombre.trim(), tarjeta)
            }) { Text("Confirmar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
