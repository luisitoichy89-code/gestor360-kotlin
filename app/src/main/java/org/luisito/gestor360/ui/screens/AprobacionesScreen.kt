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
import org.luisito.gestor360.ui.components.*
import org.luisito.gestor360.ui.viewmodels.MermaViewModel
import org.luisito.gestor360.utils.ConfigManager
import org.luisito.gestor360.utils.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AprobacionesScreen(androidId: String, rol: String = "", onBack: (() -> Unit)? = null, viewModel: MermaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val clienteId = remember { sessionManager.getClienteId() }
    // Restringido a rol == "admin" en general, no solo al "admin general" (el
    // primero creado): no tengo forma de saber cuál admin fue el primero sin
    // una consulta al backend que liste todos los admins del negocio (no
    // existe ese RPC todavía). Si más adelante agregas una columna tipo
    // "es_admin_general" en tu tabla de usuarios, avísame y lo conecto aquí
    // en una línea para restringirlo solo a ese admin específico.
    val puedeConfigurar = rol == "admin"
    var smsActivo by remember { mutableStateOf(ConfigManager.confirmacionSmsActiva(context, clienteId)) }

    LaunchedEffect(androidId) { viewModel.cargarPendientes(androidId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Aprobaciones generales", fontWeight = FontWeight.Bold) }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, actions = { IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, null) } }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Pegado arriba de todo lo demás en la tarjeta de Aprobaciones.
            ConfirmacionSmsCard(
                activo = smsActivo,
                habilitado = puedeConfigurar,
                onCambiar = { nuevoValor ->
                    smsActivo = nuevoValor
                    ConfigManager.setConfirmacionSmsActiva(context, clienteId, nuevoValor)
                }
            )
            Spacer(Modifier.height(16.dp))
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error") { viewModel.refrescar() }
                uiState.pendientes.isEmpty() -> EstadoVacio("No hay solicitudes pendientes")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.pendientes, key = { it.id }) { merma -> MermaCard(merma, uiState.isSaving, { viewModel.aprobar(merma) }, { viewModel.rechazar(merma) }) }
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
            }
            Spacer(Modifier.height(10.dp))
            Text("Cantidad: ${merma.cantidad}", fontWeight = FontWeight.Medium)
            if (!merma.motivo.isNullOrBlank()) { Spacer(Modifier.height(4.dp)); Text("Motivo: ${merma.motivo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(6.dp))
            Text("Solicitado por: ${merma.solicitado_por_nombre ?: "Usuario #${merma.solicitado_por}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAprobar, enabled = !isSaving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Aprobar") }
                OutlinedButton(onClick = onRechazar, enabled = !isSaving, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text("Rechazar") }
            }
        }
    }
}
