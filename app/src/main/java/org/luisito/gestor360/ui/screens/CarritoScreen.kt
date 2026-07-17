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
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.local.entities.TarjetaEntity
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.data.sms.SmsPagoReceiver
import org.luisito.gestor360.ui.components.EsperandoPagoOverlay
import org.luisito.gestor360.ui.components.formatearMonto
import org.luisito.gestor360.ui.viewmodels.SaleViewModel
import org.luisito.gestor360.ui.viewmodels.TarjetaViewModel

private fun ciValida(ci: String): Boolean {
    if (ci.length != 11 || !ci.all { it.isDigit() }) return false
    val mes = ci.substring(2, 4).toIntOrNull() ?: return false
    val dia = ci.substring(4, 6).toIntOrNull() ?: return false
    if (mes !in 1..12) return false
    val diasEnMes = when (mes) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> 29
        else -> 0
    }
    return dia in 1..diasEnMes
}

private sealed class PasoCheckout {
    object Ninguno : PasoCheckout()
    object ConfirmarEfectivo : PasoCheckout()
    data class EsperandoSMS(val monto: Double, val tipo: String) : PasoCheckout() // "Total" o "Mixto"
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
    val tarjeta: TarjetaEntity? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarritoScreen(
    androidId: String,
    onBack: () -> Unit,
    onVentaConfirmada: () -> Unit,
    viewModel: SaleViewModel = viewModel(),
    tarjetaViewModel: TarjetaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val tarjetaUiState by tarjetaViewModel.uiState.collectAsState()
    var paso by remember { mutableStateOf<PasoCheckout>(PasoCheckout.Ninguno) }
    var datosMixto by remember { mutableStateOf(DatosMixto()) }
    var metodoVisual by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Bug: esta pantalla nunca pedía las tarjetas, por eso no aparecían
    // (ni para vendedor ni para admin) al ir a cobrar una venta.
    LaunchedEffect(androidId) { tarjetaViewModel.cargar(androidId) }
    val tarjetasActivas = tarjetaUiState.tarjetas.filter { it.activo }
    // Si el admin no activó "Confirmación x SMS" en Aprobaciones, el checkout
    // se comporta exactamente como antes: del carrito directo a datos del
    // cliente, sin pasar por el overlay de espera de SMS.
    val smsActivo = remember {
        val sm = org.luisito.gestor360.utils.SessionManager(context)
        org.luisito.gestor360.utils.ConfigManager.confirmacionSmsActiva(context, sm.getClienteId())
    }

    // Observar SMS entrantes cuando estamos en espera
    LaunchedEffect(paso) {
        if (paso is PasoCheckout.EsperandoSMS) {
            val esperando = paso as PasoCheckout.EsperandoSMS
            SmsPagoReceiver.iniciarEspera(esperando.monto)
            SmsPagoReceiver.resultFlow.collect { resultado ->
                if (resultado.success) {
                    SmsPagoReceiver.detenerEspera()
                    when (esperando.tipo) {
                        "Total" -> paso = PasoCheckout.DatosTransferencia
                        "Mixto" -> paso = PasoCheckout.DatosMixtoTransferencia
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.ventaConfirmada) {
        if (uiState.ventaConfirmada) {
            viewModel.limpiarVentaConfirmada()
            onVentaConfirmada()
        }
    }

    // Mostrar overlay cuando estamos esperando SMS
    if (paso is PasoCheckout.EsperandoSMS) {
        val esperando = paso as PasoCheckout.EsperandoSMS
        EsperandoPagoOverlay(
            montoEsperado = esperando.monto,
            onCancelarPago = {
                SmsPagoReceiver.detenerEspera()
                paso = PasoCheckout.Ninguno
            },
            onConfirmarVisual = {
                SmsPagoReceiver.detenerEspera()
                metodoVisual = true
                when (esperando.tipo) {
                    "Total" -> paso = PasoCheckout.DatosTransferencia
                    "Mixto" -> paso = PasoCheckout.DatosMixtoTransferencia
                }
            }
        )
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
                                    Text("${item.cantidad.toInt()} × ${formatearMonto(item.subtotal)} CUP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledIconButton(
                                    onClick = { viewModel.quitarDelCarrito(i) },
                                    modifier = Modifier.size(48.dp)
                                ) { Icon(Icons.Default.Delete, "Eliminar", modifier = Modifier.size(24.dp)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", fontWeight = FontWeight.Bold)
                        Text("${formatearMonto(uiState.totalCarrito)} CUP", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        { paso = PasoCheckout.ConfirmarEfectivo }, Modifier.weight(1f).height(64.dp),
                        enabled = !uiState.isSaving, shape = RoundedCornerShape(14.dp)
                    ) { Text("EFC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Button(
                        { paso = if (smsActivo) PasoCheckout.EsperandoSMS(uiState.totalCarrito, "Total") else PasoCheckout.DatosTransferencia }, Modifier.weight(1f).height(64.dp),
                        enabled = !uiState.isSaving, shape = RoundedCornerShape(14.dp)
                    ) { Text("TFR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    Button(
                        { paso = PasoCheckout.MontoMixto }, Modifier.weight(1f).height(64.dp),
                        enabled = !uiState.isSaving, shape = RoundedCornerShape(14.dp)
                    ) { Text("MXT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    when (paso) {
        is PasoCheckout.Ninguno -> {}
        is PasoCheckout.EsperandoSMS -> { /* el overlay se muestra arriba */ }

        is PasoCheckout.ConfirmarEfectivo -> AlertDialog(
            onDismissRequest = { paso = PasoCheckout.Ninguno },
            title = { Text("Confirmar venta", fontWeight = FontWeight.Bold) },
            text = {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Total a cobrar", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("${formatearMonto(uiState.totalCarrito)} CUP", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            },
            confirmButton = {
                BotonesDialogoGrandes(
                    textoConfirmar = if (uiState.isSaving) "Guardando..." else "Aceptar",
                    confirmarHabilitado = !uiState.isSaving,
                    onCancelar = { paso = PasoCheckout.Ninguno },
                    onConfirmar = {
                        viewModel.confirmarVenta("cash", uiState.totalCarrito, 0.0, 0L, null)
                        paso = PasoCheckout.Ninguno
                    }
                )
            }
        )

        is PasoCheckout.DatosTransferencia -> DatosClienteDialog(
            titulo = "Transferencia",
            monto = uiState.totalCarrito,
            tarjetas = tarjetasActivas,
            onDismiss = { paso = PasoCheckout.Ninguno; metodoVisual = false }
        ) { ci, tel, nombre, tarjeta ->
            val metodo = if (metodoVisual) "transfer_visual" else "transfer"
            viewModel.confirmarVenta(metodo, 0.0, uiState.totalCarrito, 0L, SaleRepository.DatosCliente(ci, tel, nombre, tarjeta?.let { "${it.nombre} · ${it.numeroCuenta}" }, tarjeta?.id))
            paso = PasoCheckout.Ninguno
            metodoVisual = false
        }

        is PasoCheckout.MontoMixto -> {
            var efTexto by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { paso = PasoCheckout.Ninguno },
                title = { Text("Pago mixto", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Total: ${formatearMonto(uiState.totalCarrito)} CUP")
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
                            Text("Restante: ${formatearMonto(uiState.totalCarrito - ef)} CUP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    val ef = efTexto.toDoubleOrNull() ?: 0.0
                    BotonesDialogoGrandes(
                        textoConfirmar = "Continuar",
                        confirmarHabilitado = ef > 0 && ef < uiState.totalCarrito,
                        onCancelar = { paso = PasoCheckout.Ninguno },
                        onConfirmar = {
                            datosMixto = datosMixto.copy(efectivo = ef)
                            paso = if (smsActivo) PasoCheckout.EsperandoSMS(uiState.totalCarrito - ef, "Mixto") else PasoCheckout.DatosMixtoTransferencia
                        }
                    )
                }
            )
        }

        is PasoCheckout.DatosMixtoTransferencia -> {
            val restante = uiState.totalCarrito - datosMixto.efectivo
            DatosClienteDialog(
                titulo = "Mixto - Transferencia",
                monto = restante,
                tarjetas = tarjetasActivas,
                onDismiss = { paso = PasoCheckout.MontoMixto; metodoVisual = false }
            ) { ci, tel, nombre, tarjeta ->
                val metodo = if (metodoVisual) "mixed_visual" else "mixed"
                datosMixto = datosMixto.copy(ci = ci, tel = tel, nombre = nombre, tarjeta = tarjeta)
                viewModel.confirmarVenta(metodo, datosMixto.efectivo, restante, 0L, SaleRepository.DatosCliente(ci, tel, nombre, tarjeta?.let { "${it.nombre} · ${it.numeroCuenta}" }, tarjeta?.id))
                paso = PasoCheckout.Ninguno
                datosMixto = DatosMixto()
                metodoVisual = false
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
                                Text("💵 Efectivo: ${formatearMonto(datosMixto.efectivo)} CUP", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("📲 Transferencia: ${formatearMonto(restante)} CUP", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("💰 Total: ${formatearMonto(uiState.totalCarrito)} CUP", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        datosMixto.tarjeta?.let { Text("Cuenta: ${it.nombre} · ${it.numeroCuenta}") }
                        Text("Cliente: ${datosMixto.nombre}")
                        Text("CI: ${datosMixto.ci}")
                        Text("Teléfono: ${datosMixto.tel}")
                    }
                },
                confirmButton = {
                    BotonesDialogoGrandes(
                        textoConfirmar = if (uiState.isSaving) "Guardando..." else "Confirmar",
                        confirmarHabilitado = !uiState.isSaving,
                        onCancelar = { paso = PasoCheckout.MontoMixto },
                        onConfirmar = {
                            viewModel.confirmarVenta(
                                "mixed", datosMixto.efectivo, restante, 0L,
                                SaleRepository.DatosCliente(datosMixto.ci, datosMixto.tel, datosMixto.nombre, datosMixto.tarjeta?.let { "${it.nombre} · ${it.numeroCuenta}" }, datosMixto.tarjeta?.id)
                            )
                            paso = PasoCheckout.Ninguno
                            datosMixto = DatosMixto()
                        }
                    )
                }
            )
        }
    }
}

/**
 * Fila de botones grande dividida a la mitad (Cancelar / Confirmar), para que
 * sea fácil de presionar. Se usa en el slot confirmButton de los AlertDialog,
 * dejando dismissButton vacío, así ocupa el ancho completo del diálogo.
 */
@Composable
private fun BotonesDialogoGrandes(
    textoConfirmar: String,
    confirmarHabilitado: Boolean,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit,
    textoCancelar: String = "Cancelar"
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onCancelar,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text(textoCancelar, fontWeight = FontWeight.Bold) }
        Button(
            onClick = onConfirmar,
            enabled = confirmarHabilitado,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(textoConfirmar, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatosClienteDialog(titulo: String, monto: Double, tarjetas: List<TarjetaEntity>, onDismiss: () -> Unit, onConfirmar: (String, String, String, TarjetaEntity?) -> Unit) {
    var ci by remember { mutableStateOf("") }
    val ciError = remember(ci) { ci.isNotBlank() && !ciValida(ci) }
    var tel by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    // Ya no se preselecciona ninguna tarjeta: el vendedor/admin ve "Seleccionar"
    // y elegir cuenta es opcional, para no bloquear la venta por datos.
    var tarjetaSel by remember { mutableStateOf<TarjetaEntity?>(null) }
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
                        Text("${formatearMonto(monto)} CUP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (tarjetas.isEmpty()) {
                    Text("No hay tarjetas disponibles", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    ExposedDropdownMenuBox(menuAbierto, { menuAbierto = it }) {
                        OutlinedTextField(
                            tarjetaSel?.let { "${it.nombre} · ${it.numeroCuenta}" } ?: "Seleccionar", {},
                            readOnly = true, label = { Text("Cuenta destino (opcional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuAbierto) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(14.dp)
                        )
                        ExposedDropdownMenu(menuAbierto, { menuAbierto = false }) {
                            tarjetas.forEach { t -> DropdownMenuItem(text = { Text("${t.nombre} · ${t.numeroCuenta}") }, onClick = { tarjetaSel = t; menuAbierto = false }) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(ci, { ci = it.filter { c -> c.isDigit() }.take(11) }, label = { Text("CI (opcional)") }, placeholder = { Text("AAMMDD + 5 dígitos") }, isError = ciError, supportingText = { if (ciError) Text("El CI debe tener 11 dígitos y comenzar con la fecha de nacimiento (AAMMDD)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tel, { tel = it.filter { c -> c.isDigit() } }, label = { Text("Teléfono (opcional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
        },
        confirmButton = {
            BotonesDialogoGrandes(
                textoConfirmar = "Confirmar",
                confirmarHabilitado = !ciError,
                onCancelar = onDismiss,
                onConfirmar = { onConfirmar(ci.trim(), tel.trim(), nombre.trim(), tarjetaSel) }
            )
        }
    )
}
