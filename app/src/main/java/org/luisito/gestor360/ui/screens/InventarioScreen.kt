package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.*
import org.luisito.gestor360.ui.components.*
import org.luisito.gestor360.ui.viewmodels.InventarioViewModel
import org.luisito.gestor360.utils.CsvExporter
import org.luisito.gestor360.utils.ReporteExporter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val VENTAS_POR_PAGINA = 20

/**
 * "Inventario" (antes "Cierre de caja"): la hoja completa de un día operativo
 * de este local. No hay botón de "abrir turno" — el primer registro del día
 * (una venta, un producto nuevo, una merma...) lo abre solo en el servidor
 * (fn_asegurar_turno_abierto). Lo único manual acá es "Cerrar turno", y solo
 * para el día de hoy. Para días anteriores, con el botón de calendario, la
 * pantalla queda en solo lectura (y solo un admin puede pedirlos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarioScreen(androidId: String, rol: String, onBack: (() -> Unit)? = null, viewModel: InventarioViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val esAdmin = rol == "admin"
    var mostrarDatePicker by remember { mutableStateOf(false) }
    var mostrarCerrarTurno by remember { mutableStateOf(false) }
    var mostrarMenuExportar by remember { mutableStateOf(false) }
    var paginaVentas by remember { mutableStateOf(0) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }
    LaunchedEffect(uiState.dia) { paginaVentas = 0 }

    val dia = uiState.dia
    val ventasNoAnuladas = dia?.ventas?.filter { !it.anulada } ?: emptyList()
    val totalPaginasVentas = maxOf(1, (ventasNoAnuladas.size + VENTAS_POR_PAGINA - 1) / VENTAS_POR_PAGINA)
    val paginaSegura = paginaVentas.coerceIn(0, totalPaginasVentas - 1)
    val ventasPagina = ventasNoAnuladas.drop(paginaSegura * VENTAS_POR_PAGINA).take(VENTAS_POR_PAGINA)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Inventario", fontWeight = FontWeight.Bold) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    // Botón pequeño de búsqueda: abre el almanaque para elegir el día a revisar.
                    IconButton(onClick = { mostrarDatePicker = true }) { Icon(Icons.Default.CalendarMonth, "Elegir día") }
                    IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, null) }
                    if (dia != null && ventasNoAnuladas.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { mostrarMenuExportar = true }) { Icon(Icons.Default.FileDownload, null) }
                            DropdownMenu(expanded = mostrarMenuExportar, onDismissRequest = { mostrarMenuExportar = false }) {
                                val datos = construirDatosExportacion(dia, ventasNoAnuladas)
                                DropdownMenuItem(text = { Text("PDF") }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { mostrarMenuExportar = false; ReporteExporter.exportarPdf(context, datos) })
                                DropdownMenuItem(text = { Text("TXT") }, leadingIcon = { Icon(Icons.Default.Description, null) }, onClick = { mostrarMenuExportar = false; ReporteExporter.exportarTxt(context, datos) })
                                DropdownMenuItem(text = { Text("Word") }, leadingIcon = { Icon(Icons.Default.Article, null) }, onClick = { mostrarMenuExportar = false; ReporteExporter.exportarWord(context, datos) })
                                DropdownMenuItem(text = { Text("CSV") }, leadingIcon = { Icon(Icons.Default.TableChart, null) }, onClick = { mostrarMenuExportar = false; CsvExporter.exportarCierreCaja(context, datos.fecha, datos.productosVendidos, datos.totalEfectivo, datos.totalTransferencia, datos.totalMixto, datos.totalMixtoEfectivo, datos.totalMixtoTransferencia, datos.apertura) })
                                DropdownMenuItem(text = { Text("Compartir") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { mostrarMenuExportar = false; ReporteExporter.compartirTexto(context, datos) })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> EstadoCargando()
            uiState.error != null -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
            dia == null -> EstadoVacio("Sin datos")
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                item { EncabezadoDia(uiState.fecha, formatter, dia.solo_lectura) }

                item { TurnoCard(dia.turno, uiState.esHoy && !dia.solo_lectura, uiState.isSaving, onCerrar = { mostrarCerrarTurno = true }) }

                item { SeccionTitulo("Productos nuevos", Icons.Default.AddBox) }
                if (dia.productos_nuevos.isEmpty()) item { TextoVacioSeccion("Sin productos nuevos este día") }
                items(dia.productos_nuevos, key = { "pn_${it.id}" }) { p -> ProductoInfoRow(p) }

                item { SeccionTitulo("Productos modificados", Icons.Default.Edit) }
                if (dia.productos_modificados.isEmpty()) item { TextoVacioSeccion("Sin productos modificados este día") }
                items(dia.productos_modificados, key = { "pm_${it.id}" }) { p -> ProductoInfoRow(p) }

                item { SeccionTitulo("Devueltos", Icons.Default.AssignmentReturn) }
                if (dia.devueltos.isEmpty()) item { TextoVacioSeccion("Sin devoluciones este día") }
                items(dia.devueltos, key = { "dv_${it.id}" }) { d -> DevueltoInfoRow(d) }

                // A partir de acá sí van los montos de dinero — arriba, a propósito, no.
                item { Spacer(Modifier.height(4.dp)); Divider(); Spacer(Modifier.height(4.dp)) }
                item { SeccionTitulo("Ventas", Icons.Default.PointOfSale) }
                item { TotalesVentasCard(dia.totales_ventas) }
                if (ventasNoAnuladas.isEmpty()) item { TextoVacioSeccion("Sin ventas este día") }
                items(ventasPagina, key = { "vt_${it.id}" }) { v -> VentaInfoRow(v) }
                if (ventasNoAnuladas.isNotEmpty()) item { Paginador(paginaSegura, totalPaginasVentas) { paginaVentas = it } }

                item { Spacer(Modifier.height(56.dp)) }
            }
        }
    }

    if (mostrarCerrarTurno && dia != null) {
        val turno = dia.turno
        CerrarTurnoDialog((turno?.apertura ?: 0.0) + dia.totalEsperadoEnCaja(), uiState.isSaving, { mostrarCerrarTurno = false }) {
            viewModel.cerrarTurno(it); mostrarCerrarTurno = false
        }
    }

    if (mostrarDatePicker) {
        val hoy = LocalDate.now()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.fecha.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val fecha = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                    return !fecha.isAfter(hoy)
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { mostrarDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val fecha = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        if (fecha == hoy || esAdmin) viewModel.seleccionarFecha(fecha)
                    }
                    mostrarDatePicker = false
                }) { Text("Ver este día") }
            },
            dismissButton = { TextButton(onClick = { mostrarDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = datePickerState) }
    }
}

/** Efectivo que debería haber en caja: apertura + efectivo de ventas + parte en efectivo de las mixtas. */
private fun InventarioDia.totalEsperadoEnCaja(): Double = totales_ventas.efectivo

@Composable
private fun EncabezadoDia(fecha: LocalDate, formatter: DateTimeFormatter, soloLectura: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Today, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(fecha.format(formatter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("(24 horas de ese día)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (soloLectura) {
            Spacer(Modifier.weight(1f))
            AssistChip(onClick = {}, enabled = false, label = { Text("Solo lectura") }, leadingIcon = { Icon(Icons.Default.Lock, null, Modifier.size(16.dp)) })
        }
    }
}

@Composable
private fun TurnoCard(turno: TurnoInfo?, puedeCerrar: Boolean, isSaving: Boolean, onCerrar: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (turno == null) {
                Text("Sin actividad registrada todavía", fontWeight = FontWeight.Bold)
                Text("El turno se abre solo con el primer registro del día (una venta, un producto, etc.)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (turno.cierre == null) Icons.Default.LockOpen else Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (turno.cierre == null) "Turno abierto" else "Turno cerrado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            FilaConRol("Abierto por", turno.usuario_nombre, turno.usuario_rol, turno.created_at)
            if (turno.cierre != null) {
                Spacer(Modifier.height(4.dp))
                val dif = turno.diferencia ?: 0.0
                Text(
                    when { dif > 0 -> "Sobran $dif CUP"; dif < 0 -> "Faltan ${-dif} CUP"; else -> "Cuadra exacto ✅" },
                    fontWeight = FontWeight.Bold,
                    color = when { dif > 0 -> MaterialTheme.colorScheme.tertiary; dif < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }
                )
            } else if (puedeCerrar) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onCerrar, enabled = !isSaving, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text("Cerrar turno")
                }
            }
        }
    }
}

@Composable
private fun SeccionTitulo(texto: String, icono: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TextoVacioSeccion(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 26.dp))
}

/** Fecha + quién (nombre y rol) hizo cada cosa: reemplaza a lo que antes mostraba la pantalla de Trazas. */
@Composable
private fun FilaConRol(etiqueta: String, nombre: String?, rol: String?, fecha: String?) {
    Text(
        buildString {
            append(etiqueta); append(": "); append(nombre ?: "—")
            if (!rol.isNullOrBlank()) append(" (${if (rol == "admin") "admin" else "vendedor"})")
            if (!fecha.isNullOrBlank()) append(" · ").append(fecha.take(16).replace("T", " "))
        },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ProductoInfoRow(p: ProductoInfo) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold)
            Text("Stock actual: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall)
            if (!p.fecha.isNullOrBlank()) Text(p.fecha.take(16).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DevueltoInfoRow(d: DevueltoInfo) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(d.producto_nombre, fontWeight = FontWeight.Bold)
                EstadoDevolucionChip(d.estado)
            }
            Text("Cantidad: ${d.cantidad}  ·  Método: ${d.metodo}", style = MaterialTheme.typography.bodySmall)
            FilaConRol(if (d.estado == "pendiente") "Solicitado por" else "Resuelto por", d.resuelto_por_nombre ?: d.solicitado_por_nombre, d.resuelto_por_rol, d.fecha)
        }
    }
}

@Composable
private fun EstadoDevolucionChip(estado: String) {
    val (texto, color) = when (estado) {
        "aprobada_stock" -> "Vuelve a stock" to MaterialTheme.colorScheme.primary
        "aprobada_merma" -> "Merma" to MaterialTheme.colorScheme.error
        "rechazada" -> "Rechazada" to MaterialTheme.colorScheme.error
        else -> "Pendiente" to MaterialTheme.colorScheme.tertiary
    }
    Text(texto, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
}

@Composable
private fun VentaInfoRow(v: VentaInfo) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(v.producto_nombre, fontWeight = FontWeight.Bold)
                Text("${v.total} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("Cantidad: ${v.cantidad}  ·  Método: ${v.metodo}", style = MaterialTheme.typography.bodySmall)
            FilaConRol("Vendido por", v.usuario_nombre, v.usuario_rol, v.fecha)
        }
    }
}

@Composable
private fun TotalesVentasCard(totales: TotalesVentas) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            FilaResumenDinero("Efectivo", totales.efectivo)
            FilaResumenDinero("Transferencia", totales.transferencia)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            FilaResumenDinero("Total (${totales.cantidad_ventas} ventas)", totales.efectivo + totales.transferencia, destacado = true)
        }
    }
}

@Composable
private fun FilaResumenDinero(etiqueta: String, valor: Double, destacado: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text("$valor CUP", style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal, color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CerrarTurnoDialog(efectivoEsperado: Double, isSaving: Boolean, onDismiss: () -> Unit, onCerrar: (Double) -> Unit) {
    var monto by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp), title = { Text("Cerrar turno", fontWeight = FontWeight.Bold) }, text = { Column {
        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Text("Esperado: $efectivoEsperado CUP", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(monto, { monto = it }, label = { Text("Efectivo contado") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
    } }, confirmButton = { TextButton(enabled = (monto.toDoubleOrNull() ?: -1.0) >= 0 && !isSaving, onClick = { onCerrar(monto.toDoubleOrNull() ?: 0.0) }) { Text(if (isSaving) "Cerrando..." else "Cerrar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

private fun construirDatosExportacion(dia: InventarioDia, ventas: List<VentaInfo>): ReporteExporter.DatosCierreCaja {
    val productosVendidos = ventas.groupBy { it.producto_nombre }.map { (nombre, filas) -> nombre to filas.sumOf { it.cantidad } }.sortedByDescending { it.second }
    val mixtas = ventas.filter { it.metodo == "mixed" }
    return ReporteExporter.DatosCierreCaja(
        fecha = dia.fecha,
        productosVendidos = productosVendidos,
        totalEfectivo = ventas.filter { it.metodo == "cash" }.sumOf { it.total },
        totalTransferencia = ventas.filter { it.metodo == "transfer" }.sumOf { it.total },
        totalMixto = mixtas.sumOf { it.total },
        totalMixtoEfectivo = mixtas.sumOf { it.efectivo },
        totalMixtoTransferencia = mixtas.sumOf { it.transferencia },
        apertura = dia.turno?.apertura ?: 0.0
    )
}
