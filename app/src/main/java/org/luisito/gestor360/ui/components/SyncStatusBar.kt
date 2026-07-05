package org.luisito.gestor360.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.ui.viewmodels.SyncViewModel

/**
 * Franja compacta que se puede meter arriba de cualquier pantalla: muestra
 * cuántas acciones faltan por sincronizar, si hay conflictos sin resolver, y
 * un botón para forzar la sincronización ya mismo.
 */
@Composable
fun SyncStatusBar(
    androidId: String,
    onVerConflictos: () -> Unit,
    viewModel: SyncViewModel = viewModel()
) {
    val pendientes by viewModel.pendientes.collectAsState()
    val conflictos by viewModel.conflictos.collectAsState()
    val sincronizando by viewModel.sincronizando.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(mensaje) {
        if (mensaje != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.limpiarMensaje()
        }
    }

    if (conflictos.isNotEmpty()) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, onClick = onVerConflictos) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${conflictos.size} conflicto(s) por revisar — toca para verlos",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (pendientes > 0 || mensaje != null) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sincronizando) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (pendientes > 0) Icons.Default.CloudOff else Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    mensaje ?: "$pendientes cambio(s) sin sincronizar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                if (!sincronizando && pendientes > 0) {
                    TextButton(onClick = { viewModel.sincronizarAhora(androidId) }) {
                        Text("Sincronizar")
                    }
                }
            }
        }
    }
}
