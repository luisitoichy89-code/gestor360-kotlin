package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.utils.SessionManager
import org.luisito.gestor360.data.models.*
import org.luisito.gestor360.ui.components.*
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
import org.luisito.gestor360.ui.viewmodels.InventarioViewModel
import org.luisito.gestor360.ui.viewmodels.CierrePaso
import org.luisito.gestor360.ui.viewmodels.EstadoPaso
import org.luisito.gestor360.utils.CsvExporter
import org.luisito.gestor360.utils.ReporteExporter
import org.luisito.gestor360.ui.theme.NeuCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val VENTAS_POR_PAGINA = 20

private data class TarjetaResumen(val nombre: String, val numero: String, val titular: String?, val total: Double)
private data class VendedorFiltro(val nombre: String, val turnoIds: List<Long>)

private enum class PasoCierreTurno { NINGUNO, CONFIRMAR, PIN, VERIFICANDO, PENDIENTES, MONTO, PROCESANDO, EXITOSO }

private data class TonoSeccion(val fondo: Color, val acento: Color)

private val TonoVendedores = TonoSeccion(Color(0xFFE0F7FA), Color(0xFF00838F))
private val TonoTarjeta = TonoSeccion(Color(0xFFE8EAF6), Color(0xFF3949AB))
private val TonoProductosVendidos = TonoSeccion(Color(0xFFE0F2F1), Color(0xFF00695C))
private val TonoPagosTarjeta = TonoSeccion(Color(0xFFEDE7F6), Color(0xFF512DA8))
private val TonoProductosIngresados = TonoSeccion(Color(0xFFF1F8E9), Color(0xFF558B2F))
private val TonoProductosModificados = TonoSeccion(Color(0xFFFFF3E0), Color(0xFFEF6C00))
private val TonoProductosEliminados = TonoSeccion(Color(0xFFFFEBEE), Color(0xFFC62828))
private val TonoSolicitudes = TonoSeccion(Color(0xFFF3E5F5), Color(0xFF8E24AA))
private val TonoDevueltos = TonoSeccion(Color(0xFFFBE9E7), Color(0xFFD84315))
private val TonoMermas = TonoSeccion(Color(0xFFFCE4EC), Color(0xFFAD1457))
private val TonoDetalleVentas = TonoSeccion(Color(0xFFE1F5FE), Color(0xFF0277BD))
private val TonoEfectivo = TonoSeccion(Color(0xFFE8F5E9), Color(0xFF2E7D32))
private val TonoTransferencia = TonoSeccion(Color(0xFFE3F2FD), Color(0xFF1565C0))
private val TonoTotalGenerado = TonoSeccion(Color(0xFFFFF8E1), Color(0xFFF57F17))

private fun InventarioDia.filtradoPorUsuario(usuarioId: Long?): InventarioDia {
    val ventasPropias = ventas.filter { it.usuario_id != null && it.usuario_id == usuarioId }
    val productosVendidosPropios = ventasPropias
        .groupBy { it.producto_nombre }
        .map { (nombre, filas) ->
            ProductoVendidoInfo(
                nombre = nombre,
                cantidadVendida = filas.sumOf { it.cantidad },
                total_vendido = filas.sumOf { it.total }
            )
        }
    val conTarjeta = ventasPropias.filter { !it.tarjeta_numero.isNullOrBlank() }
    return copy(
        ventas = ventasPropias,
        productos_vendidos = productosVendidosPropios,
        totales_ventas = TotalesVentas(
            efectivo = ventasPropias.sumOf { it.efectivo },
            transferencia = ventasPropias.sumOf { it.transferencia },
            tarjeta = conTarjeta.sumOf { it.total },
            total = ventasPropias.sumOf { it.total },
            cantidad_ventas = ventasPropias.size.toLong()
        ),
        totales_por_tarjeta = conTarjeta
            .groupBy { it.tarjeta_banco ?: "Tarjeta" }
            .map { (nombre, filas) -> TotalTarjetaInfo(nombre = nombre, total = filas.sumOf { it.total }) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarioScreen(
    androidId: String,
    titulo: String = "Inventario",
    mostrarBotonVentasRealizadas: Boolean = true,
    esVistaPersonal: Boolean = false,
    turnoId: Long? = null,
    onBack: (() -> Unit)? = null,
    onVerVentasRealizadas: () -> Unit = {},
    onVerHistorialTurnos: () -> Unit = {},
    viewModel: InventarioViewModel = viewModel(),
    accesoViewModel: AccesoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val esAdmin = sessionManager.getRol() == "admin"
    val esHistorico = turnoId != null
    var verMisVentasHistorico by remember(turnoId) { mutableStateOf(!esAdmin) }
    val vistaPersonalEfectiva = if (esHistorico) verMisVentasHistorico else esVistaPersonal
    val mostrarControlesDeLocal = esAdmin && !vistaPersonalEfectiva && !esHistorico
    var pasoCierreTurno by remember { mutableStateOf(PasoCierreTurno.NINGUNO) }
    var mostrarMenuExportar by remember { mutableStateOf(false) }
    var paginaVentas by remember { mutableStateOf(0) }

    LaunchedEffect(androidId, turnoId) {
        if (turnoId != null) {
            viewModel.cargarTurnoHistorico(androidId, turnoId)
        } else {
            viewModel.cargar(androidId, esVistaPersonal)
        }
    }
    LaunchedEffect(uiState.dia) { paginaVentas = 0 }
    val diaCrudo = uiState.dia
    val dia = remember(diaCrudo, esHistorico, vistaPersonalEfectiva) {
        if (esHistorico && vistaPersonalEfectiva && diaCrudo != null) {
            diaCrudo.filtradoPorUsuario(sessionManager.getUserId())
        } else diaCrudo
    }
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
                NeuCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }; Spacer(Modifier.width(4.dp)) }
                        TituloPantalla(titulo, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (mostrarControlesDeLocal) { IconoAccionBandeja(Icons.Default.LockOpen, "Cerrar turno", tint = MaterialTheme.colorScheme.error) { pasoCierreTurno = PasoCierreTurno.CONFIRMAR } }
                                if (!esHistorico) {
                                    IconoAccionBandeja(Icons.Default.History, "Historial de turnos") { onVerHistorialTurnos() }
                                }
                                IconoAccionBandeja(Icons.Default.Refresh, "Actualizar") { viewModel.refrescar() }
                                if (mostrarBotonVentasRealizadas && dia != null && ventasNoAnuladas.isNotEmpty()) {
                                    Box {
                                        IconoAccionBandeja(Icons.Default.FileDownload, "Descargar") { mostrarMenuExportar = true }
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
                    }
                }
                if (!esHistorico) {
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

                        if (mostrarControlesDeLocal && uiState.turnosDelDia.isNotEmpty()) {
                            item { SeccionTitulo("Vendedores", Icons.Default.Groups, TonoVendedores) }
                            item {
                                val vendedores = remember(uiState.turnosDelDia) {
                                    uiState.turnosDelDia
                                        .groupBy { it.usuario_nombre ?: "Sin nombre" }
                                        .map { (nombre, turnos) -> VendedorFiltro(nombre, turnos.map { it.id }) }
                                        .sortedBy { it.nombre }
                                }
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val todosSeleccionados = uiState.turnosSeleccionadosIds.size == uiState.turnosDelDia.size
                                    FilterChip(
                                        selected = todosSeleccionados,
                                        onClick = { viewModel.seleccionarTodosLosTurnos() },
                                        label = { Text("Todos") },
                                        leadingIcon = if (todosSeleccionados) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null
                                    )
                                    vendedores.forEach { vendedor ->
                                        val seleccionado = vendedor.turnoIds.isNotEmpty() && uiState.turnosSeleccionadosIds.containsAll(vendedor.turnoIds)
                                        FilterChip(
                                            selected = seleccionado,
                                            onClick = { viewModel.toggleVendedorSeleccionado(vendedor.turnoIds) },
                                            label = { Text(vendedor.nombre) },
                                            leadingIcon = if (seleccionado) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null
                                        )
                                    }
                                }
                            }
                        }

                        if (esHistorico && esAdmin) {
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !verMisVentasHistorico,
                                        onClick = { verMisVentasHistorico = false },
                                        label = { Text("Inventario general") },
                                        leadingIcon = if (!verMisVentasHistorico) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null
                                    )
                                    FilterChip(
                                        selected = verMisVentasHistorico,
                                        onClick = { verMisVentasHistorico = true },
                                        label = { Text("Mis ventas") },
                                        leadingIcon = if (verMisVentasHistorico) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null
                                    )
                                }
                            }
                        }

                        item { TotalesGeneralesCard(dia.totales_ventas) }
                        item { SeccionTitulo("Tarjeta", Icons.Default.CreditCard, TonoTarjeta) }
                        if (tarjetasResumen.isEmpty()) item { TextoVacioSeccion("Sin cobros por tarjeta") }
                        items(tarjetasResumen, key = { "tj_${it.nombre}_${it.numero}" }) { t -> TarjetaResumenRow(t) }

                        item { SeccionTitulo("Productos vendidos", Icons.Default.PointOfSale, TonoProductosVendidos) }
                        if (dia.productos_vendidos.isEmpty()) item { TextoVacioSeccion("Sin ventas") }
                        items(dia.productos_vendidos, key = { "pv_${it.nombre}" }) { p -> ProductoVendidoRow(p) }

                        item { SeccionTitulo("Pagos por tarjetas", Icons.Default.CreditCard, TonoPagosTarjeta) }
                        if (pagosPorTarjeta.isEmpty()) item { TextoVacioSeccion("Sin pagos por tarjeta") }
                        items(pagosPorTarjeta, key = { "pg_${it.id}" }) { v -> PagoTarjetaRow(v) }

                        item { SeccionTitulo(if (vistaPersonalEfectiva) "Productos ingresados" else "Productos nuevos ingresados", Icons.Default.AddBox, TonoProductosIngresados) }
                        if (dia.productos_nuevos.isEmpty()) item { TextoVacioSeccion("Sin productos nuevos") }
                        items(dia.productos_nuevos, key = { "pn_${it.id}" }) { p -> ProductoNuevoRow(p, mostrarSolicitadoPor = vistaPersonalEfectiva) }

                        if (!vistaPersonalEfectiva) {
                            item { Spacer(Modifier.height(4.dp)); Divider(); Spacer(Modifier.height(4.dp)) }
                            item { SeccionTitulo("Productos modificados", Icons.Default.Edit, TonoProductosModificados) }
                            if (dia.productos_modificados.isEmpty()) item { TextoVacioSeccion("Sin productos modificados") }
                            items(dia.productos_modificados, key = { "pm_${it.id}" }) { p -> ProductoInfoRow(p) }

                            item { SeccionTitulo("Productos eliminados", Icons.Default.Delete, TonoProductosEliminados) }
                            if (dia.productos_eliminados.isEmpty()) item { TextoVacioSeccion("Sin productos eliminados") }
                            items(dia.productos_eliminados, key = { "pe_${it.id}" }) { p -> ProductoEliminadoRow(p) }
                        }

                        if (vistaPersonalEfectiva) {
                            item { Spacer(Modifier.height(4.dp)); Divider(); Spacer(Modifier.height(4.dp)) }
                            item { SeccionTitulo("Solicitudes y aumentos", Icons.Default.PendingActions, TonoSolicitudes) }
                            if (dia.solicitudes.isEmpty()) item { TextoVacioSeccion("Sin solicitudes") }
                            items(dia.solicitudes, key = { "sa_${it.id}" }) { s -> SolicitudRow(s) }
                        }

                        item { SeccionTitulo("Devueltos", Icons.Default.AssignmentReturn, TonoDevueltos) }
                        if (dia.devueltos.isEmpty()) item { TextoVacioSeccion("Sin devoluciones") }
                        items(dia.devueltos, key = { "dv_${it.id}" }) { d -> DevueltoInfoRow(d, mostrarSolicitadoPor = vistaPersonalEfectiva) }

                        item { SeccionTitulo("Mermas", Icons.Default.Warning, TonoMermas) }
                        if (dia.mermas.isEmpty()) item { TextoVacioSeccion("Sin mermas") }
                        items(dia.mermas, key = { "me_${it.id}" }) { m -> MermaInfoRow(m, esVistaPersonal = vistaPersonalEfectiva) }

                        item { Spacer(Modifier.height(4.dp)); Divider(); Spacer(Modifier.height(4.dp)) }
                        item { SeccionTitulo("Detalle de ventas", Icons.Default.Receipt, TonoDetalleVentas) }
                        if (ventasNoAnuladas.isEmpty()) item { TextoVacioSeccion("Sin ventas") }
                        items(ventasPagina, key = { "vt_${it.id}" }) { v -> VentaInfoRow(v) }
                        if (ventasNoAnuladas.isNotEmpty()) item { PaginacionBar(pagina = paginaSegura, totalPaginas = totalPaginasVentas, onPaginaAnterior = { paginaVentas = paginaSegura - 1 }, onPaginaSiguiente = { paginaVentas = paginaSegura + 1 }) }

                        item { Spacer(Modifier.height(56.dp)) }
                    }
                }
            }
        }
    }

    if (pasoCierreTurno == PasoCierreTurno.CONFIRMAR) {
        AlertDialog(
            onDismissRequest = { pasoCierreTurno = PasoCierreTurno.NINGUNO },
            icon = { Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Cerrar turno de todo el local?") },
            text = { Text("Esto reinicia a cero el inventario y las ventas de TODOS los vendedores de este local.") },
            confirmButton = { TextButton(onClick = { pasoCierreTurno = PasoCierreTurno.PIN }) { Text("Continuar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pasoCierreTurno = PasoCierreTurno.NINGUNO }) { Text("Cancelar") } }
        )
    }

    if (pasoCierreTurno == PasoCierreTurno.PIN) {
        ConfirmarPinDialog(
            accesoViewModel = accesoViewModel,
            onCancelar = { pasoCierreTurno = PasoCierreTurno.NINGUNO },
            onPinCorrecto = {
                pasoCierreTurno = PasoCierreTurno.VERIFICANDO
                viewModel.verificarCierre()
            }
        )
    }

    if (pasoCierreTurno == PasoCierreTurno.VERIFICANDO) {
        val pasos = uiState.pasosCierre
        val verificacionTerminada = pasos.isNotEmpty() && pasos.all { it.estado == EstadoPaso.COMPLETADO || it.estado == EstadoPaso.ERROR }
        val hayErrores = pasos.any { it.estado == EstadoPaso.ERROR }

        VerificandoCierreDialog(
            pasos = pasos,
            terminado = verificacionTerminada,
            hayErrores = hayErrores,
            onCancelar = { pasoCierreTurno = PasoCierreTurno.NINGUNO },
            onReintentar = {
                pasoCierreTurno = PasoCierreTurno.VERIFICANDO
                viewModel.verificarCierre()
            }
        )

        LaunchedEffect(verificacionTerminada) {
            if (verificacionTerminada) {
                if (hayErrores) {
                    pasoCierreTurno = PasoCierreTurno.PENDIENTES
                } else {
                    pasoCierreTurno = PasoCierreTurno.MONTO
                }
            }
        }
    }

    if (pasoCierreTurno == PasoCierreTurno.PENDIENTES) {
        AlertDialog(
            onDismissRequest = { pasoCierreTurno = PasoCierreTurno.NINGUNO },
            icon = { Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Pendientes por resolver") },
            text = {
                Column {
                    Text("Debes resolver lo siguiente antes de cerrar el turno:")
                    Spacer(Modifier.height(8.dp))
                    uiState.pendientesCierre.forEach { p ->
                        Text("• $p", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pasoCierreTurno = PasoCierreTurno.NINGUNO }) { Text("Entendido") } },
            dismissButton = null
        )
    }

    if (pasoCierreTurno == PasoCierreTurno.MONTO && dia != null) {
        val turno = dia.turno
        CerrarTurnoDialog(
            (turno?.apertura ?: 0.0) + dia.totalEsperadoEnCaja(),
            uiState.isSaving,
            { pasoCierreTurno = PasoCierreTurno.NINGUNO }
        ) {
            pasoCierreTurno = PasoCierreTurno.PROCESANDO
            viewModel.cerrarTurno(it)
        }
    }

    if (pasoCierreTurno == PasoCierreTurno.PROCESANDO) {
        CerrandoTurnoDialog()
        LaunchedEffect(uiState.cierreExitoso, uiState.error) {
            if (uiState.cierreExitoso) {
                pasoCierreTurno = PasoCierreTurno.EXITOSO
            } else if (uiState.error != null) {
                pasoCierreTurno = PasoCierreTurno.NINGUNO
            }
        }
    }

    if (pasoCierreTurno == PasoCierreTurno.EXITOSO) {
        AlertDialog(
            onDismissRequest = { pasoCierreTurno = PasoCierreTurno.NINGUNO },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = androidx.compose.ui.graphics.Color(0xFF43A047)) },
            title = { Text("Turno cerrado exitosamente") },
            text = { Text("Se ha abierto un nuevo turno automáticamente.") },
            confirmButton = {
                TextButton(onClick = {
                    pasoCierreTurno = PasoCierreTurno.NINGUNO
                    viewModel.refrescar()
                }) { Text("Continuar") }
            }
        )
    }
}

private fun InventarioDia.totalEsperadoEnCaja(): Double = totales_ventas.efectivo

@Composable
private fun TituloPantalla(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.2.sp,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun IconoAccionBandeja(
    icono: ImageVector,
    descripcion: String?,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val presionado by interactionSource.collectIsPressedAsState()
    val fondo = if (presionado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
    val colorIcono = if (presionado) MaterialTheme.colorScheme.surface else tint
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(fondo)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, descripcion, tint = colorIcono, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SeccionTitulo(
    texto: String,
    icono: ImageVector,
    tono: TonoSeccion = TonoSeccion(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
) {
    Surface(shape = RoundedCornerShape(8.dp), color = tono.fondo) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Icon(icono, null, tint = tono.acento, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(texto.uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tono.acento, letterSpacing = 0.6.sp)
        }
    }
}

@Composable
private fun TextoVacioSeccion(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EstadoError(mensaje: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(mensaje, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun ProductoNuevoRow(p: ProductoInfo, mostrarSolicitadoPor: Boolean = false) {
    val colorFondo = if (p.eliminado) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val colorTexto = if (p.eliminado) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = colorFondo, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(p.nombre, fontWeight = FontWeight.Bold, color = colorTexto)
                if (p.eliminado) Text("ELIMINADO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            if (p.eliminado) {
                Text("Stock al eliminar: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Stock: ${p.stock.toInt()}  ·  Precio: ${formatearMonto(p.precio)} CUP", style = MaterialTheme.typography.bodySmall)
            }
            if (mostrarSolicitadoPor) p.solicitado_por_nombre?.let { Text("Solicitado por: $it", style = MaterialTheme.typography.labelSmall) }
            p.resuelto_por_nombre?.let { Text("Aprobado por: $it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ProductoInfoRow(p: ProductoInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold)
            Text("Stock: ${p.stock.toInt()}  ·  Precio: ${formatearMonto(p.precio)} CUP", style = MaterialTheme.typography.bodySmall)
            p.categoria?.let { Text("Categoría: $it", style = MaterialTheme.typography.labelSmall) }
            p.resuelto_por_nombre?.let { Text("Modificado por: $it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ProductoEliminadoRow(p: ProductoEliminadoInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("Stock al eliminar: ${p.stock.toInt()}", style = MaterialTheme.typography.bodySmall)
            p.resuelto_por_nombre?.let { Text("Eliminado por: $it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun DevueltoInfoRow(d: DevueltoInfo, mostrarSolicitadoPor: Boolean = false) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(d.producto_nombre, fontWeight = FontWeight.Bold)
            Text("Cantidad: ${d.cantidad.toInt()}  ·  Método: ${d.metodo}", style = MaterialTheme.typography.bodySmall)
            EstadoDevolucionChip(d.estado)
            if (mostrarSolicitadoPor) {
                d.solicitado_por_nombre?.let { Text("Solicitado por: $it", style = MaterialTheme.typography.labelSmall) }
                d.resuelto_por_nombre?.let { Text("Aprobado por: $it", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun MermaInfoRow(m: MermaInfo, esVistaPersonal: Boolean = false) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(m.producto_nombre, fontWeight = FontWeight.Bold)
            Text("Cantidad: ${m.cantidad.toInt()}  ·  Estado: ${m.estado}", style = MaterialTheme.typography.bodySmall)
            Text("Motivo: ${m.motivo}", style = MaterialTheme.typography.bodySmall)
            m.solicitado_por_nombre?.let { Text("Solicitado por: $it", style = MaterialTheme.typography.labelSmall) }
            m.resuelto_por_nombre?.let { Text("${if (esVistaPersonal) "Aprobado" else "Resuelto"} por: $it", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun SolicitudRow(s: SolicitudInfo) {
    val aprobado = s.estado == "aprobado"
    val colorAcento = if (aprobado) androidx.compose.ui.graphics.Color(0xFF43A047) else androidx.compose.ui.graphics.Color(0xFFFFA000)
    val etiquetaEstado = when (s.estado) {
        "aprobado" -> "APROBADO"
        "rechazado" -> "RECHAZADO"
        "cancelado" -> "CANCELADO"
        else -> "PENDIENTE"
    }
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = colorAcento.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(s.producto_nombre, fontWeight = FontWeight.Bold)
                Text(etiquetaEstado, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colorAcento)
            }
            Text(
                if (s.tipo == "aumento") "Cantidad: ${s.cantidad.toInt()}  ·  Nuevo precio: ${formatearMonto(s.precio)} CUP"
                else "Cantidad: ${s.cantidad.toInt()}",
                style = MaterialTheme.typography.bodySmall
            )
            s.solicitado_por_nombre?.let { Text("Solicitado por: $it", style = MaterialTheme.typography.labelSmall) }
            if (aprobado) {
                s.resuelto_por_nombre?.let { Text("Aprobado por: $it", style = MaterialTheme.typography.labelSmall, color = colorAcento, fontWeight = FontWeight.Medium) }
            }
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
            Text("Cantidad: ${v.cantidad.toInt()}  ·  Método: ${v.metodo}", style = MaterialTheme.typography.bodySmall)
            FilaConRol("Vendido por", v.usuario_nombre, v.usuario_rol, v.fecha)
        }
    }
}

@Composable
private fun TotalesGeneralesCard(totales: TotalesVentas) {
    NeuCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilaResumenDinero("Total efectivo", totales.efectivo, Icons.Default.Payments, TonoEfectivo)
            FilaResumenDinero("Total transferencia", totales.transferencia, Icons.Default.SwapHoriz, TonoTransferencia)
            FilaResumenDinero("Total generado (${totales.cantidad_ventas} ventas)", totales.efectivo + totales.transferencia, Icons.Default.TrendingUp, TonoTotalGenerado, destacado = true)
        }
    }
}

@Composable
private fun FilaResumenDinero(etiqueta: String, valor: Double, icono: ImageVector, tono: TonoSeccion, destacado: Boolean = false) {
    Surface(shape = RoundedCornerShape(10.dp), color = tono.fondo, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (destacado) 12.dp else 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icono, null, tint = tono.acento, modifier = Modifier.size(if (destacado) 22.dp else 18.dp))
                Spacer(Modifier.width(8.dp))
                Text(etiqueta, style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (destacado) FontWeight.Bold else FontWeight.Medium, color = tono.acento)
            }
            Text("${formatearMonto(valor)} CUP", style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tono.acento)
        }
    }
}

@Composable
private fun TarjetaResumenRow(t: TarjetaResumen) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(t.nombre, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(t.numero, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!t.titular.isNullOrBlank()) Text(t.titular, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text("${formatearMonto(t.total)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun ProductoVendidoRow(p: ProductoVendidoInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(p.nombre, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Total vendidos: ${p.total_vendido.toInt()}", style = MaterialTheme.typography.bodySmall)
            Text("Total actual: ${p.total_actual.toInt()}", style = MaterialTheme.typography.bodySmall)
            Text("Total agregados: ${p.total_agregado.toInt()}", style = MaterialTheme.typography.bodySmall)
            Text("Total merma: ${p.total_merma.toInt()}", style = MaterialTheme.typography.bodySmall)
            Text("Total inicial: ${p.total_inicial.toInt()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PagoTarjetaRow(v: VentaInfo) {
    NeuCard(shape = RoundedCornerShape(12.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Text("${v.tarjeta_banco ?: ""} · ${v.tarjeta_numero}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
                Text("${formatearMonto(v.transferencia)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
            }
            if (!v.tarjeta_titular.isNullOrBlank()) Text("Titular de la cuenta: ${v.tarjeta_titular}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("Cliente: ${v.cliente_nombre?.takeIf { it.isNotBlank() } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("Teléfono: ${v.cliente_tel?.takeIf { it.isNotBlank() } ?: "—"}", style = MaterialTheme.typography.bodySmall)
            Text("CI: ${v.cliente_ci?.takeIf { it.isNotBlank() } ?: "—"}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CerrarTurnoDialog(efectivoEsperado: Double, isSaving: Boolean, onDismiss: () -> Unit, onCerrar: (Double) -> Unit) {
    var monto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("Cerrar turno", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                NeuCard(shape = RoundedCornerShape(14.dp), containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Esperado: ${formatearMonto(efectivoEsperado)} CUP", Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    monto, { monto = it },
                    label = { Text("Efectivo contado") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = (monto.toDoubleOrNull() ?: -1.0) >= 0 && !isSaving,
                onClick = { onCerrar(monto.toDoubleOrNull() ?: 0.0) }
            ) { Text("Cerrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") } }
    )
}

@Composable
private fun CerrandoTurnoDialog() {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        NeuCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(0.82f)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text("Cerrando turno...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text("No cierres la app ni cambies de pantalla", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun VerificandoCierreDialog(
    pasos: List<CierrePaso>,
    terminado: Boolean,
    hayErrores: Boolean,
    onCancelar: () -> Unit,
    onReintentar: () -> Unit
) {
    val errores = pasos.count { it.estado == EstadoPaso.ERROR }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        NeuCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(0.9f)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)) {
                Text("Verificando cierre de turno", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (terminado && hayErrores) "Encontramos algunos problemas" else "No cierres la app ni cambies de pantalla",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    pasos.forEach { paso -> FilaPasoCierre(paso) }
                }
                if (terminado && hayErrores) {
                    Spacer(Modifier.height(20.dp))
                    NeuCard(shape = RoundedCornerShape(14.dp), containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            "$errores de ${pasos.size} verificaciones no se pudieron completar. Revisa la conexión e inténtalo de nuevo.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancelar, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancelar") }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onReintentar) { Text("Reintentar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaPasoCierre(paso: CierrePaso) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (paso.estado) {
                EstadoPaso.COMPLETADO -> Icon(Icons.Default.CheckCircle, null, tint = androidx.compose.ui.graphics.Color(0xFF43A047), modifier = Modifier.size(22.dp))
                EstadoPaso.ERROR -> Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                EstadoPaso.EN_PROGRESO -> CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                EstadoPaso.PENDIENTE -> Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                paso.nombre,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (paso.estado == EstadoPaso.PENDIENTE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            paso.detalle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (paso.estado == EstadoPaso.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FilaConRol(etiqueta: String, nombre: String?, rol: String?, fecha: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$etiqueta: ${nombre ?: "—"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fecha?.take(10) ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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

private fun formatearMonto(valor: Double): String {
    return if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()
}
