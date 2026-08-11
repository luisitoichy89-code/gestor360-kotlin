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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.data.sync.NetworkMonitor
import org.luisito.gestor360.ui.theme.NeuCard
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

data class VentaAgrupada(
    val id: String,
    val hora: String,
    val productos: String,
    val total: Double,
    val metodo: String,
    val efectivo: Double,
    val transferencia: Double,
    val tarjeta: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisVentasScreen(androidId: String, onBack: () -> Unit) {
    val context = AppContextHolder.context
    val db = remember { AppDatabase.obtener(context) }
    val session = remember { SessionManager(context) }
    val saleRepository = remember { SaleRepository() }

    var ventas by remember { mutableStateOf<List<VentaAgrupada>>(emptyList()) }
    var totalEfectivo by remember { mutableStateOf(0.0) }
    var totalTransferencia by remember { mutableStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var trigger by remember { mutableStateOf(0) }

    suspend fun cargarDesdeRoom() {
        val localId = session.getLocalId() ?: return

        if (NetworkMonitor.hayInternet(context)) {
            try {
                saleRepository.getSales(androidId)
            } catch (e: Exception) {
                android.util.Log.e("MisVentasScreen", "No se pudo sincronizar ventas con el servidor, se usa Room", e)
            }
        }

        val usuarioId = session.getUserId()
        val turnoActivo = db.turnoDao().obtenerActivo(localId)
        val turnoActivoId = turnoActivo?.id

        val todasVentas = db.ventaDao().obtenerTodas(localId)
            .filter { it.usuarioId == usuarioId }

        val ventasTurno = if (turnoActivoId != null) {
            todasVentas.filter { v ->
                if (v.turnoId != 0L) v.turnoId == turnoActivoId
                else {
                    val aperturaStr = turnoActivo?.createdAt
                    v.createdAt != null && aperturaStr != null && v.createdAt >= aperturaStr
                }
            }
        } else {
            todasVentas
        }

        val sorted = ventasTurno.sortedByDescending { it.createdAt }
        val agrupadas = sorted.map { v ->
            val nombreProducto = v.productoNombre
                ?: db.productoDao().obtenerPorId(v.productoId.toString(), localId)?.nombre
                ?: "Producto #${v.productoId}"
            val nombreTarjeta = if (v.tarjetaId != null) {
                db.tarjetaDao().obtenerPorId(v.tarjetaId, localId)?.nombre
            } else null

            VentaAgrupada(
                id = v.id,
                hora = v.createdAt?.substring(11, 16) ?: "--:--",
                productos = "${nombreProducto} x${v.cantidad.toInt()}",
                total = v.total,
                metodo = v.metodo,
                efectivo = v.efectivo,
                transferencia = v.transferencia,
                tarjeta = nombreTarjeta
            )
        }

        ventas = agrupadas
        totalEfectivo = ventasTurno.sumOf { it.efectivo }
        totalTransferencia = ventasTurno.sumOf { it.transferencia }
    }

    LaunchedEffect(androidId, trigger) {
        if (trigger == 0) isLoading = true else isRefreshing = true
        cargarDesdeRoom()
        isLoading = false
        isRefreshing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Ventas") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { trigger++ }, enabled = !isRefreshing) {
                        if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, "Refrescar")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    NeuCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Totales del turno", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Efectivo:")
                                Text("${formatearMonto(totalEfectivo)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Transferencia:")
                                Text("${formatearMonto(totalTransferencia)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Divider(Modifier.padding(vertical = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total:", fontWeight = FontWeight.Bold)
                                Text("${formatearMonto(totalEfectivo + totalTransferencia)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("${ventas.size} ventas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (ventas.isEmpty()) {
                    item {
                        Text("Sin ventas en este turno", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(ventas, key = { it.id }) { venta ->
                    NeuCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(venta.hora, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(venta.productos, fontWeight = FontWeight.Bold)
                                Text(venta.metodo, style = MaterialTheme.typography.bodySmall)
                                if (venta.tarjeta != null) {
                                    Text("💳 ${venta.tarjeta}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("${formatearMonto(venta.total)} CUP", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun formatearMonto(valor: Double): String {
    return if (valor == valor.toLong().toDouble()) valor.toLong().toString() else "%.2f".format(valor)
}
