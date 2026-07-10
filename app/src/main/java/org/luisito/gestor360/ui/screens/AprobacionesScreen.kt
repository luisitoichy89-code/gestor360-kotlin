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
import org.luisito.gestor360.data.models.MermaPendiente
import org.luisito.gestor360.data.repository.AprobacionStock
import org.luisito.gestor360.ui.components.*
import org.luisito.gestor360.ui.viewmodels.AprobacionStockViewModel
import org.luisito.gestor360.ui.viewmodels.MermaViewModel
import org.luisito.gestor360.utils.ConfigManager
import org.luisito.gestor360.utils.SessionManager

/**
 * Todas las solicitudes que necesitan aprobación del admin en un solo lugar:
 * mermas propuestas + producto nuevo / aumento de stock / anular venta
 * (antes esta pantalla solo mostraba mermas — las otras solicitudes se
 * guardaban bien en el servidor pero nadie tenía forma de verlas ni
 * resolverlas desde acá).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AprobacionesScreen(
    androidId: String, rol: String = "", onBack: (() -> Unit)? = null,
    viewModel: MermaViewModel = viewModel(),
    aprobacionVM: AprobacionStockViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val aprobUiState by aprobacionVM.uiState.collectAsState()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val clienteId = remember { sessionManager.getClienteId() }
    val userId = remember { sessionManager.getUserId() }
    val puedeConfigurar = rol == "admin"
    var smsActivo by remember { mutableStateOf(ConfigManager.confirmacionSmsActiva(context, clienteId)) }

    LaunchedEffect(androidId) { viewModel.cargarPendientes(androidId); aprobacionVM.cargar(androidId) }

    val totalPendientes = uiState.pendientes.size + aprobUiState.pendientes.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Aprobaciones generales", fontWeight = FontWeight.Bold) }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { viewModel.refrescar(); aprobacionVM.cargar(androidId) }) { Icon(Icons.Default.Refresh, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            ConfirmacionSmsCard(
                activo = smsActivo,
                habilitado = puedeConfigurar,
                onCambiar = { nuevoValor -> smsActivo = nuevoValor; ConfigManager.setConfirmacionSmsActiva(context, clienteId, nuevoValor) }
            )
            Spacer(Modifier.height(16.dp))
            when {
                uiState.isLoading || aprobUiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
                aprobUiState.error != null -> EstadoError(aprobUiState.error ?: "Error") { aprobacionVM.cargar(androidId) }
                totalPendientes == 0 -> EstadoVacio("No hay solicitudes pendientes")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(aprobUiState.pendientes.filter { it.id != null }, key = { "stock_${it.id}" }) { sol ->
                        val solId = sol.id!!
                        AprobacionStockCard(sol, aprobUiState.isSaving,
                            onAprobar = { aprobacionVM.resolver(solId, "aprobada", userId) },
                            onRechazar = { aprobacionVM.resolver(solId, "rechazada", userId) })
                    }
                    items(uiState.pendientes, key = { "merma_${it.id}" }) { merma -> MermaCard(merma, uiState.isSaving, { viewModel.aprobar(merma) }, { viewModel.rechazar(merma) }) }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ConfirmacionSmsCard(activo: Boolean, habilitado: Boolean, onCambiar: (Boolean) -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Sms, null, tint = if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Confirmación x SMS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (activo) "Activado: las ventas TFR y MXT esperan el SMS de PAGOxMOVIL antes de continuar."
                    else "Desactivado: del carrito se pasa directo a datos del cliente, como antes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!habilitado) {
                    Spacer(Modifier.height(2.dp))
                    Text("Solo un admin puede cambiar esto.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = activo, onCheckedChange = onCambiar, enabled = habilitado)
        }
    }
}

@Composable
private fun MermaCard(merma: MermaPendiente, isSaving: Boolean, onAprobar: () -> Unit, onRechazar: () -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(10.dp))
                Text(merma.producto_nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Merma", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text("Cantidad: ${merma.cantidad}", fontWeight = FontWeight.Medium)
            if (!merma.motivo.isNullOrBlank()) { Spacer(Modifier.height(4.dp)); Text("Motivo: ${merma.motivo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(6.dp))
            Text("Solicitado por: ${merma.solicitado_por_nombre ?: "Usuario #${merma.solicitado_por}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!merma.created_at.isNullOrBlank()) Text(merma.created_at.take(16).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAprobar, enabled = !isSaving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Aprobar") }
                OutlinedButton(onClick = onRechazar, enabled = !isSaving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text("Rechazar") }
            }
        }
    }
}

@Composable
private fun AprobacionStockCard(sol: AprobacionStock, isSaving: Boolean, onAprobar: () -> Unit, onRechazar: () -> Unit) {
    val (icono, etiqueta) = when (sol.tipo) {
        "producto_nuevo" -> Icons.Default.AddBox to "Producto nuevo"
        "aumento_stock" -> Icons.Default.Add to "Aumento de stock"
        "anular_venta" -> Icons.Default.Cancel to "Anular venta"
        else -> Icons.Default.FactCheck to sol.tipo
    }
    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icono, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (sol.tipo == "anular_venta") "Venta #${sol.venta_id?.take(8) ?: ""}" else sol.producto_nombre,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            when (sol.tipo) {
                "producto_nuevo" -> { Text("Precio: ${sol.precio ?: 0.0} CUP  ·  Cantidad: ${sol.cantidad}", fontWeight = FontWeight.Medium) }
                "aumento_stock" -> { Text("Cantidad a agregar: ${sol.cantidad}", fontWeight = FontWeight.Medium) }
                "anular_venta" -> { Text("Total de la venta: ${sol.venta_total ?: 0.0} CUP", fontWeight = FontWeight.Medium) }
            }
            Spacer(Modifier.height(6.dp))
            Text("Solicitado por: ${sol.solicitado_por_nombre ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!sol.created_at.isNullOrBlank()) Text(sol.created_at.take(16).replace("T", " "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAprobar, enabled = !isSaving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Aprobar") }
                OutlinedButton(onClick = onRechazar, enabled = !isSaving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text("Rechazar") }
            }
        }
    }
}
