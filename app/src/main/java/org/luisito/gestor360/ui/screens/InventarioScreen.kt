package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
import org.luisito.gestor360.ui.viewmodels.InventarioViewModel
import org.luisito.gestor360.utils.CsvExporter
import org.luisito.gestor360.utils.ReporteExporter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.luisito.gestor360.ui.theme.NeuCard

private const val VENTAS_POR_PAGINA = 20

private data class TarjetaResumen(val nombre: String, val numero: String, val titular: String?, val total: Double)

private enum class PasoCierreTurno { NINGUNO, CONFIRMAR, PENDIENTES, PIN, MONTO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarioScreen(
    androidId: String,
    rol: String,
    onBack: (() -> Unit)? = null,
    onVerVentasRealizadas: () -> Unit = {},
    viewModel: InventarioViewModel = viewModel(),
    accesoViewModel: AccesoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val esAdmin = rol == "admin"
    var mostrarDatePicker by remember { mutableStateOf(false) }
    var pasoCierreTurno by remember { mutableStateOf(PasoCierreTurno.NINGUNO) }
    var mostrarMenuExportar by remember { mutableStateOf(false) }
    var paginaVentas by remember { mutableStateOf(0) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    LaunchedEffect(androidId) { viewModel.cargar(androidId, esAdmin) }
    LaunchedEffect(uiState.dia) { paginaVentas = 0 }

    val dia = uiState.dia
    val ventasNoAnuladas = dia?.ventas?.filter { !it.anulada } ?: emptyList()
    val totalPaginasVentas = maxOf(1, (ventasNoAnuladas.size + VENTAS_POR_PAGINA - 1) / VENTAS_POR_PAGINA)
    val paginaSegura = paginaVentas.coerceIn(0, totalPaginasVentas - 1)
    val ventasPagina = ventasNoAnuladas.drop(paginaSegura * VENTAS_POR_PAGINA).take(VENTAS_POR_PAGINA)

    val tarjetasResumen = remember(ventasNoAnuladas) {
        ventasNoAnuladas.filter { !it.tarjeta_numero.isNullOrBlank() }
            .groupBy { Triple(it.tarjeta_banco, it.tarjeta_numero, it.tarjeta_titular) }
            .map { (clave, filas) -> TarjetaResumen(clave.first ?: "Tarjeta", clave.second ?: "", clave.third, filas.sumOf { it.transferencia }) }
            .sortedByDescending { it.total }
    }

    val pagosPorTarjeta = remember(ventasNoAnuladas) {
        ventasNoAnuladas.filter { (it.metodo == "transfer" || it.metodo == "transfer_visual" || it.metodo == "mixed" || it.metodo == "mixed_visual") && !it.tarjeta_numero.isNullOrBlank() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                NeuCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), shape = RoundedCornerShape(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }; Spacer(Modifier.width(4.dp)) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inventario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(uiState.fecha.format(formatter), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (dia?.solo_lectura == true) { Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Lock, "Solo lectura", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp)) }
                            }
                        }
                        if (esAdmin && uiState.esHoy && dia?.solo_lectura != true) { IconButton(onClick = { pasoCierreTurno = PasoCierreTurno.CONFIRMAR }) { Icon(Icons.Default.LockOpen, "Cerrar turno", tint = MaterialTheme.colorScheme.error) } }
                        IconButton(onClick = { mostrarDatePicker = true }) { Icon(Icons.Default.CalendarMonth, "Elegir día", tint = MaterialTheme.colorScheme.onSurface) }
                        IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurface) }
                        if (dia != null && ventasNoAnuladas.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { mostrarMenuExportar = true }) { Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.onSurface) }
                                DropdownMenu(expanded = mostrarMenuExportar, onDismissRequest = { mostrarMenuExportar = false }) {
                                    DropdownMenuItem(text = { Text("PDF") }, leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }, onClick = { mostrarMenuExportar = false; ReporteExporter.exportarPdf(context, dia) })
                                    DropdownMenuItem(text = { Text("TXT") }, leadingIcon = { Icon(Icons.Default.Description, null) }, onClick = { mostrarMenuExportar = false; ReporteExporter.exportarTxt(context, dia) })
                                    DropdownMenuItem(text = { Text("Word") }, leadingIcon = { Icon(Icons.Default.Article, null) }, onClick = { mostrarMenuExportar = false; ReporteExporter.exportarWord(context, dia) })
                                    DropdownMenuItem(text = { Text("CSV") }, leadingIcon = { Icon(Icons.Default.TableChart, null) }, onClick = { mostrarMenuExportar = false; CsvExporter.exportarInventario(context, dia) })
                                    DropdownMenuItem(text = { Text("Compartir") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { mostrarMenuExportar = false; ReporteExporter.compartirTexto(context, dia) })
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = onVerVentasRealizadas,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("VENTAS REALIZADAS", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        val estadoPantalla = when { uiState.isLoading -> "cargando"; uiState.error != null -> "error"; dia == null -> "vacio"; else -> "listo" }
        androidx.compose.animation.Crossfade(targetState = estadoPantalla, modifier = Modifier.fillMaxSize().padding(padding), label = "inventario_transicion") { estado ->
            when (estado) {
                "cargando" -> SkeletonLista()
                "error" -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
                "vacio" -> EstadoVacio("Sin datos")
                else -> {
                    val dia = dia!!
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                        if (esAdmin && !uiState.esHoy && uiState.turnosDelDia.isNotEmpty()) {
                            item { SeccionTitulo("Turnos de este día", Icons.Default.Groups) }
                            item {
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val todosSeleccionados = uiState.turnosSeleccionadosIds.size == uiState.turnosDelDia.size
                                    FilterChip(
                                        selected = todosSeleccionados,
                                        onClick = { viewModel.seleccionarTodosLosTurnos() },
                                        label = { Text("Todos") },
                                        leadingIcon = if (todosSeleccionados) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null
                                    )
                                    uiState.turnosDelDia.forEach { turno ->
                                        val seleccionado = turno.id in uiState.turnosSeleccionadosIds
                                        val etiqueta = turno.usuario_nombre?.let { "$it · ${turno.created_at?.substring(11, 16) ?: ""}" } ?: "Turno ${turno.created_at?.take(16) ?: turno.id}"
                                        FilterChip(
                                            selected = seleccionado,
                                            onClick = { viewModel.toggleTurnoSeleccionado(turno.id) },
                                            label = { Text(etiqueta) },
                                            leadingIcon = if (seleccionado) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null
                                        )
                                    }
                                }
                            }
                        }

                        item { TotalesGeneralesCard(dia.totales_ventas) }
                        item { SeccionTitulo("Tarjeta", Icons.Default.CreditCard) }
                        if (tarjetasResumen.isEmpty()) item { TextoVacioSeccion("Sin cobros por tarjeta este día") }
                        items(tarjetasResumen, key = { "tj_${it.nombre}_${it.numero}" }) { t -> TarjetaResumenRow(t) }

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

                        item { SeccionTitulo("Mermas", Icons.Default.Warning) }
                        if (dia.mermas.isEmpty()) item { TextoVacioSeccion("Sin mermas este día") }
                        items(dia.mermas, key = { "me_${it.id}" }) { m -> MermaInfoRow(m) }

                        item { Spacer(Modifier.height(4.dp)); Divider(); Spacer(Modifier.height(4.dp)) }
                        item { SeccionTitulo("Detalle de ventas", Icons.Default.Receipt) }
                        if (ventasNoAnuladas.isEmpty()) item { TextoVacioSeccion("Sin ventas este día") }
                        items(ventasPagina, key = { "vt_${it.id}" }) { v -> VentaInfoRow(v) }
                        if (ventasNoAnuladas.isNotEmpty()) item { PaginacionBar(pagina = paginaSegura, totalPaginas = totalPaginasVentas, onPaginaAnterior = { paginaVentas = paginaSegura - 1 }, onPaginaSiguiente = { paginaVentas = paginaSegura + 1 }) }

                        item { Spacer(Modifier.height(56.dp)) }
                    }
                }
            }
        }
    }

    // Diálogo de confirmación inicial
    if (pasoCierreTurno == PasoCierreTurno.CONFIRMAR) {
        AlertDialog(
            onDismissRequest = { pasoCierreTurno = PasoCierreTurno.NINGUNO },
            shape = RoundedCornerShape(18.dp),
            title = { Text("¿Cerrar turno de todo el local?") },
            text = { Text("Se reiniciará a cero el inventario de todos los vendedores del local. Las ventas del turno cerrado se podrán ver seleccionando la fecha en el calendario.") },
            confirmButton = { TextButton(onClick = { pasoCierreTurno = PasoCierreTurno.PENDIENTES }) { Text("Continuar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pasoCierreTurno = PasoCierreTurno.NINGUNO }) { Text("Cancelar") } }
        )
    }

    // Diálogo de pendientes: bloquea si hay solicitudes sin resolver
    if (pasoCierreTurno == PasoCierreTurno.PENDIENTES) {
        AlertDialog(
            onDismissRequest = { pasoCierreTurno = PasoCierreTurno.NINGUNO },
            shape = RoundedCornerShape(18.dp),
            title = { Text("Solicitudes pendientes", fontWeight = FontWeight.Bold) },
            text = { Text("Hay solicitudes de aumento de stock, mermas, devoluciones o ingreso de productos sin resolver.\n\nDebe aprobarlas o rechazarlas antes de cerrar el turno para que queden registradas en el turno correcto.") },
            confirmButton = { TextButton(onClick = { pasoCierreTurno = PasoCierreTurno.NINGUNO }) { Text("Entendido") } }
        )
    }

    // Diálogo de PIN
    if (pasoCierreTurno == PasoCierreTurno.PIN) {
        ConfirmarPinDialog(
            accesoViewModel = accesoViewModel,
            onCancelar = { pasoCierreTurno = PasoCierreTurno.NINGUNO },
            onPinCorrecto = { pasoCierreTurno = PasoCierreTurno.MONTO }
        )
    }

    // Diálogo de monto
    if (pasoCierreTurno == PasoCierreTurno.MONTO && dia != null) {
        val efectivoEsperado = dia.totales_ventas.efectivo + dia.totales_ventas.transferencia
        CerrarTurnoDialog(
            efectivoEsperado = efectivoEsperado,
            isSaving = uiState.isSaving,
            onDismiss = { pasoCierreTurno = PasoCierreTurno.NINGUNO }
        ) { viewModel.cerrarTurno(it); pasoCierreTurno = PasoCierreTurno.NINGUNO }
    }

    if (mostrarDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { mostrarDatePicker = false }, confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> val fecha = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate(); if (fecha == LocalDate.now() || esAdmin) viewModel.seleccionarFecha(fecha) }; mostrarDatePicker = false }) { Text("Ver este día") } }, dismissButton = { TextButton(onClick = { mostrarDatePicker = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } }) { DatePicker(state = datePickerState) }
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
    Text(
        text = texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 26.dp)
    )
}

@Composable
private fun ProductoNuevoRow(p: ProductoInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold)
            Text("Stock: ${p.stock.toInt()}  ·  Precio: ${formatearMonto(p.precio)}", style = MaterialTheme.typography.bodySmall)
            p.ubicacion?.let { Text("Ubicación: $it", style = MaterialTheme.typography.labelSmall) }
            p.resuelto_por_nombre?.let { Text("Ingresado por: $it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ProductoInfoRow(p: ProductoInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold)
            Text("Stock: ${p.stock.toInt()}  ·  Precio: ${formatearMonto(p.precio)}", style = MaterialTheme.typography.bodySmall)
            p.ubicacion?.let { Text("Ubicación: $it", style = MaterialTheme.typography.labelSmall) }
            p.resuelto_por_nombre?.let { Text("Modificado por: $it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ProductoEliminadoRow(p: ProductoEliminadoInfo) { NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(p.nombre, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer); Text("Stock al eliminar: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall); p.resuelto_por_nombre?.let { Text("Eliminado por: $it", style = MaterialTheme.typography.labelSmall) } } } }

@Composable
private fun DevueltoInfoRow(d: DevueltoInfo) { NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(d.producto_nombre, fontWeight = FontWeight.Bold); Text("Cantidad: ${d.cantidad.toInt()}  ·  Método: ${d.metodo}", style = MaterialTheme.typography.bodySmall); EstadoDevolucionChip(d.estado) } } }

@Composable
private fun MermaInfoRow(m: MermaInfo) { NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(m.producto_nombre, fontWeight = FontWeight.Bold); Text("Cantidad: ${m.cantidad.toInt()}  ·  Estado: ${m.estado}", style = MaterialTheme.typography.bodySmall); Text("Motivo: ${m.motivo}", style = MaterialTheme.typography.bodySmall); m.solicitado_por_nombre?.let { Text("Solicitado por: $it", style = MaterialTheme.typography.labelSmall) }; m.resuelto_por_nombre?.let { Text("Resuelto por: $it", style = MaterialTheme.typography.labelSmall) } } } }

@Composable
private fun EstadoDevolucionChip(estado: String) { val (texto, color) = when (estado) { "aprobada_stock" -> "Vuelve a stock" to MaterialTheme.colorScheme.primary; "aprobada_merma" -> "Merma" to MaterialTheme.colorScheme.error; "rechazada" -> "Rechazada" to MaterialTheme.colorScheme.error; else -> "Pendiente" to MaterialTheme.colorScheme.tertiary }; Text(texto, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold) }

@Composable
private fun VentaInfoRow(v: VentaInfo) { NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text(v.producto_nombre, fontWeight = FontWeight.Bold); Text("${formatearMonto(v.total)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }; Text("Cantidad: ${v.cantidad.toInt()}  ·  Método: ${v.metodo}", style = MaterialTheme.typography.bodySmall); FilaConRol("Vendido por", v.usuario_nombre, v.usuario_rol, v.fecha) } } }

@Composable
private fun TotalesGeneralesCard(totales: TotalesVentas) { NeuCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { FilaResumenDinero("Total efectivo", totales.efectivo); FilaResumenDinero("Total transferencia", totales.transferencia); Divider(modifier = Modifier.padding(vertical = 8.dp)); FilaResumenDinero("Total generado (${totales.cantidad_ventas} ventas)", totales.efectivo + totales.transferencia, destacado = true) } } }

@Composable
private fun FilaResumenDinero(etiqueta: String, valor: Double, destacado: Boolean = false) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(etiqueta, style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium); Text("${formatearMonto(valor)} CUP", style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal, color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) } }

@Composable
private fun TarjetaResumenRow(t: TarjetaResumen) { NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f, fill = false)) { Text(t.nombre, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis); Text(t.numero, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (!t.titular.isNullOrBlank()) Text(t.titular, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Spacer(Modifier.width(12.dp)); Text("${formatearMonto(t.total)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false) } } }

@Composable
private fun ProductoVendidoRow(p: ProductoVendidoInfo) { NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(p.nombre, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text("Total vendidos: ${p.total_vendido.toInt()}", style = MaterialTheme.typography.bodySmall); Text("Total actual: ${p.total_actual.toInt()}", style = MaterialTheme.typography.bodySmall); Text("Total agregados: ${p.total_agregado.toInt()}", style = MaterialTheme.typography.bodySmall); Text("Total merma: ${p.total_merma.toInt()}", style = MaterialTheme.typography.bodySmall); Text("Total inicial: ${p.total_inicial.toInt()}", style = MaterialTheme.typography.bodySmall) } } }

@Composable
private fun PagoTarjetaRow(v: VentaInfo) { NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) { Text("${v.tarjeta_banco ?: ""} · ${v.tarjeta_numero}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)); Text("${formatearMonto(v.transferencia)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false) }; if (!v.tarjeta_titular.isNullOrBlank()) Text("Titular de la cuenta: ${v.tarjeta_titular}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(6.dp)); Text("Cliente: ${v.cliente_nombre?.takeIf { it.isNotBlank() } ?: "—"}", style = MaterialTheme.typography.bodySmall); Text("Teléfono: ${v.cliente_tel?.takeIf { it.isNotBlank() } ?: "—"}", style = MaterialTheme.typography.bodySmall); Text("CI: ${v.cliente_ci?.takeIf { it.isNotBlank() } ?: "—"}", style = MaterialTheme.typography.bodySmall) } } }

@Composable
private fun CerrarTurnoDialog(efectivoEsperado: Double, isSaving: Boolean, onDismiss: () -> Unit, onCerrar: (Double) -> Unit) { var monto by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(18.dp), title = { Text("Cerrar turno", fontWeight = FontWeight.Bold) }, text = { Column { NeuCard(shape = RoundedCornerShape(14.dp), containerColor = MaterialTheme.colorScheme.primaryContainer) { Text("Esperado: ${formatearMonto(efectivoEsperado)} CUP", Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(12.dp)); OutlinedTextField(monto, { monto = it }, label = { Text("Efectivo contado") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) } }, confirmButton = { TextButton(enabled = (monto.toDoubleOrNull() ?: -1.0) >= 0 && !isSaving, onClick = { onCerrar(monto.toDoubleOrNull() ?: 0.0) }) { Text(if (isSaving) "Cerrando..." else "Cerrar") } }, dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } }) }

@Composable
private fun FilaConRol(etiqueta: String, nombre: String?, rol: String?, fecha: String?) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("$etiqueta: ${nombre ?: "—"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(fecha?.take(10) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun ConfirmarPinDialog(accesoViewModel: AccesoViewModel, onCancelar: () -> Unit, onPinCorrecto: () -> Unit) {
    val uiState by accesoViewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    var validando by remember { mutableStateOf(false) }
    val bloqueado = uiState.pinBloqueado && uiState.pinBloqueadoSegundos > 0
    AlertDialog(
        onDismissRequest = onCancelar,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text("Confirma con tu PIN") },
        text = {
            Column {
                Text("Vuelve a introducir tu PIN de administrador para cerrar el turno de todo el local.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (!bloqueado && it.length <= 6) { pin = it.filter { c -> c.isDigit() }; accesoViewModel.limpiarPinError() } },
                    label = { Text("PIN") },
                    singleLine = true,
                    enabled = !bloqueado,
                    isError = uiState.pinError != null,
                    supportingText = { if (uiState.pinError != null) Text(uiState.pinError ?: "", color = MaterialTheme.colorScheme.error) else if (bloqueado) Text("Demasiados intentos. Espera ${uiState.pinBloqueadoSegundos}s.", color = MaterialTheme.colorScheme.error) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length in 4..6 && !validando && !bloqueado,
                onClick = {
                    validando = true
                    accesoViewModel.validarPin(pin) { ok ->
                        validando = false
                        if (ok) onPinCorrecto() else pin = ""
                    }
                }
            ) { Text(if (validando) "Verificando..." else "Confirmar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
