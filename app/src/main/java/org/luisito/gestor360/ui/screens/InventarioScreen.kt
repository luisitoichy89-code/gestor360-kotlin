package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
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
import org.luisito.gestor360.ui.theme.NeuCard
import org.luisito.gestor360.ui.theme.NeuButton
import org.luisito.gestor360.ui.theme.NeuOutlinedButton
import org.luisito.gestor360.ui.theme.neuShadow

private const val VENTAS_POR_PAGINA = 20

/** Fila calculada de "TARJETA": una cuenta + cuánto entró por ella ese día. */
private data class TarjetaResumen(val etiqueta: String, val titular: String?, val total: Double)

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

    // TARJETA: agrupa por cuenta y suma solo la parte transferida a esa cuenta
    // (en una venta mixta, el efectivo no cuenta para ninguna tarjeta).
    val tarjetasResumen = remember(ventasNoAnuladas) {
        ventasNoAnuladas
            .filter { !it.tarjeta_numero.isNullOrBlank() }
            .groupBy { Triple(it.tarjeta_banco, it.tarjeta_numero, it.tarjeta_titular) }
            .map { (clave, filas) -> TarjetaResumen("${clave.first ?: ""} · ${clave.second}", clave.third, filas.sumOf { it.transferencia }) }
            .sortedByDescending { it.total }
    }

    // PAGOS POR TARJETAS: cada venta por transferencia/mixta que sí tiene tarjeta seleccionada.
    val pagosPorTarjeta = remember(ventasNoAnuladas) {
        ventasNoAnuladas.filter { (it.metodo == "transfer" || it.metodo == "transfer_visual" || it.metodo == "mixed" || it.metodo == "mixed_visual") && !it.tarjeta_numero.isNullOrBlank() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                NeuCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            "Inventario",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { mostrarDatePicker = true }) { Icon(Icons.Default.CalendarMonth, "Elegir día", tint = MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurface) }
                        if (dia != null && ventasNoAnuladas.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { mostrarMenuExportar = true }) { Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.onSurface) }
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
                }
            }
        }
    ) { padding ->
        val estadoPantalla = when {
            uiState.isLoading -> "cargando"
            uiState.error != null -> "error"
            dia == null -> "vacio"
            else -> "listo"
        }
        androidx.compose.animation.Crossfade(targetState = estadoPantalla, modifier = Modifier.fillMaxSize().padding(padding), label = "inventario_transicion") { estado -> when (estado) {
            "cargando" -> SkeletonLista()
            "error" -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
            "vacio" -> EstadoVacio("Sin datos")
            else -> {
                val dia = dia!!
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { EncabezadoDia(uiState.fecha, formatter, dia.solo_lectura) }
                item { TurnoCard(dia.turno, uiState.esHoy && !dia.solo_lectura, uiState.isSaving, onCerrar = { mostrarCerrarTurno = true }) }

                item { Spacer(Modifier.height(4.dp)); Divider(); Spacer(Modifier.height(4.dp)) }
                item { TotalesGeneralesCard(dia.totales_ventas) }

                item { SeccionTitulo("Tarjeta", Icons.Default.CreditCard) }
                if (tarjetasResumen.isEmpty()) item { TextoVacioSeccion("Sin cobros por tarjeta este día") }
                items(tarjetasResumen, key = { "tj_${it.etiqueta}" }) { t -> TarjetaResumenRow(t) }

                item { SeccionTitulo("Productos vendidos", Icons.Default.PointOfSale) }
                if (dia.productos_vendidos.isEmpty()) item { TextoVacioSeccion("Sin ventas este día") }
                items(dia.productos_vendidos, key = { "pv_${it.nombre}" }) { p -> ProductoVendidoRow(p) }

                item { SeccionTitulo("Pagos por tarjetas", Icons.Default.CreditCard) }
                if (pagosPorTarjeta.isEmpty()) item { TextoVacioSeccion("Sin pagos por tarjeta este día") }
                items(pagosPorTarjeta, key = { "pg_${it.id}" }) { v -> PagoTarjetaRow(v) }

                item { SeccionTitulo("Productos nuevos ingresados", Icons.Default.AddBox) }
                if (dia.productos_nuevos.isEmpty()) item { TextoVacioSeccion("Sin productos nuevos este día") }
                items(dia.productos_nuevos, key = { "pn_${it.id}" }) { p -> ProductoNuevoRow(p) }

                item { Spacer(Modifier.height(4.dp)); Divider(); Spacer(Modifier.height(4.dp)) }
                item { SeccionTitulo("Productos modificados", Icons.Default.Edit) }
                if (dia.productos_modificados.isEmpty()) item { TextoVacioSeccion("Sin productos modificados este día") }
                items(dia.productos_modificados, key = { "pm_${it.id}" }) { p -> ProductoInfoRow(p) }
                item { SeccionTitulo("Productos eliminados", Icons.Default.Delete) }
                if (dia.productos_eliminados.isEmpty()) item { TextoVacioSeccion("Sin productos eliminados este día") }
                items(dia.productos_eliminados, key = { "pe_${it.id}" }) { p -> ProductoEliminadoRow(p) }
                item { SeccionTitulo("Devueltos", Icons.Default.AssignmentReturn) }
                if (dia.devueltos.isEmpty()) item { TextoVacioSeccion("Sin devoluciones este día") }
                items(dia.devueltos, key = { "dv_${it.id}" }) { d -> DevueltoInfoRow(d) }

                item { Spacer(Modifier.height(4.dp)); Divider(); Spacer(Modifier.height(4.dp)) }
                item { SeccionTitulo("Detalle de ventas", Icons.Default.Receipt) }
                if (ventasNoAnuladas.isEmpty()) item { TextoVacioSeccion("Sin ventas este día") }
                items(ventasPagina, key = { "vt_${it.id}" }) { v -> VentaInfoRow(v) }
                if (ventasNoAnuladas.isNotEmpty()) item {
                    PaginacionBar(
                        pagina = paginaSegura,
                        totalPaginas = totalPaginasVentas,
                        onPaginaAnterior = { paginaVentas = paginaSegura - 1 },
                        onPaginaSiguiente = { paginaVentas = paginaSegura + 1 }
                    )
                }
                item { Spacer(Modifier.height(56.dp)) }
            }
            }
        } }
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
            dismissButton = { TextButton(onClick = { mostrarDatePicker = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } }
        ) { DatePicker(state = datePickerState) }
    }
}

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
    NeuCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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
                    when { dif > 0 -> "Sobran ${formatearMonto(dif)} CUP"; dif < 0 -> "Faltan ${formatearMonto(-dif)} CUP"; else -> "Cuadra exacto ✅" },
                    fontWeight = FontWeight.Bold,
                    color = when { dif > 0 -> MaterialTheme.colorScheme.tertiary; dif < 0 -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary }
                )
            } else if (puedeCerrar) {
                Spacer(Modifier.height(10.dp))
                NeuButton(onClick = onCerrar, enabled = !isSaving, shape = RoundedCornerShape(14.dp), containerColor = MaterialTheme.colorScheme.error) {
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
        Text(texto.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TextoVacioSeccion(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 26.dp))
}

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
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold)
            Text("Stock actual: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall)
            if (!p.fecha.isNullOrBlank()) Text(p.fecha.take(16).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** PRODUCTOS NUEVOS INGRESADOS: nombre, cantidad, ubicación. Solo llegan aquí los ya aprobados. */
@Composable
private fun ProductoEliminadoRow(p: ProductoEliminadoInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold)
            Text("Stock al borrarse: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall)
            if (!p.resuelto_por_nombre.isNullOrBlank()) Text("Eliminado por: ${p.resuelto_por_nombre}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!p.fecha.isNullOrBlank()) Text(p.fecha.take(16).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProductoNuevoRow(p: ProductoInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold)
            Text("Cantidad: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall)
            Text("Ubicación: ${p.ubicacion ?: "—"}", style = MaterialTheme.typography.bodySmall)
            if (!p.solicitado_por_nombre.isNullOrBlank() && p.solicitado_por_nombre != p.resuelto_por_nombre)
                Text("Propuesto por: ${p.solicitado_por_nombre}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!p.resuelto_por_nombre.isNullOrBlank())
                Text("Registrado por: ${p.resuelto_por_nombre}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!p.fecha.isNullOrBlank()) Text(p.fecha.take(16).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DevueltoInfoRow(d: DevueltoInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
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
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(v.producto_nombre, fontWeight = FontWeight.Bold)
                Text("${formatearMonto(v.total)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("Cantidad: ${v.cantidad}  ·  Método: ${v.metodo}", style = MaterialTheme.typography.bodySmall)
            FilaConRol("Vendido por", v.usuario_nombre, v.usuario_rol, v.fecha)
        }
    }
}

/** TOTAL EFECTIVO / TOTAL TRANSFERENCIA / TOTAL GENERADO. */
@Composable
private fun TotalesGeneralesCard(totales: TotalesVentas) {
    NeuCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            FilaResumenDinero("Total efectivo", totales.efectivo)
            FilaResumenDinero("Total transferencia", totales.transferencia)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            FilaResumenDinero("Total generado (${totales.cantidad_ventas} ventas)", totales.efectivo + totales.transferencia, destacado = true)
        }
    }
}

@Composable
private fun FilaResumenDinero(etiqueta: String, valor: Double, destacado: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text("${formatearMonto(valor)} CUP", style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal, color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** Fila de la sección "Tarjeta": cuenta + cuánto entró por ella. */
@Composable
private fun TarjetaResumenRow(t: TarjetaResumen) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(t.etiqueta, fontWeight = FontWeight.Bold)
                if (!t.titular.isNullOrBlank()) Text(t.titular, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${formatearMonto(t.total)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Fila de "PRODUCTOS VENDIDOS": nombre + los 5 totales pedidos. */
@Composable
private fun ProductoVendidoRow(p: ProductoVendidoInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Total vendidos: ${p.total_vendido}", style = MaterialTheme.typography.bodySmall)
            Text("Total actual: ${p.total_actual}", style = MaterialTheme.typography.bodySmall)
            Text("Total agregados: ${p.total_agregado}", style = MaterialTheme.typography.bodySmall)
            Text("Total merma: ${p.total_merma}", style = MaterialTheme.typography.bodySmall)
            Text("Total inicial: ${p.total_inicial}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Fila de "PAGOS POR TARJETAS": cuenta + monto + datos del cliente si se cargaron. */
@Composable
private fun PagoTarjetaRow(v: VentaInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${v.tarjeta_banco ?: ""} · ${v.tarjeta_numero}", fontWeight = FontWeight.Bold)
                Text("${formatearMonto(v.transferencia)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            if (!v.tarjeta_titular.isNullOrBlank()) Text("Titular de la cuenta: ${v.tarjeta_titular}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Cliente: ${v.cliente_nombre?.takeIf { it.isNotBlank() } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("Teléfono: ${v.cliente_tel?.takeIf { it.isNotBlank() } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("CI: ${v.cliente_ci?.takeIf { it.isNotBlank() } ?: "—"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CerrarTurnoDialog(efectivoEsperado: Double, isSaving: Boolean, onDismiss: () -> Unit, onCerrar: (Double) -> Unit) {
    var monto by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp), title = { Text("Cerrar turno", fontWeight = FontWeight.Bold) }, text = { Column {
        NeuCard(shape = RoundedCornerShape(14.dp), containerColor = MaterialTheme.colorScheme.primaryContainer) { Text("Esperado: ${formatearMonto(efectivoEsperado)} CUP", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(monto, { monto = it }, label = { Text("Efectivo contado") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
    } }, confirmButton = { TextButton(enabled = (monto.toDoubleOrNull() ?: -1.0) >= 0 && !isSaving, onClick = { onCerrar(monto.toDoubleOrNull() ?: 0.0) }) { Text(if (isSaving) "Cerrando..." else "Cerrar") } }, dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } })
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
