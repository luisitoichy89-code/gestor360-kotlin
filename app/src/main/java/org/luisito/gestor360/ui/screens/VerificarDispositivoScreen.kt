package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.User
import org.luisito.gestor360.ui.viewmodels.AccesoViewModel
import org.luisito.gestor360.utils.DeviceIdManager

@Composable
fun VerificarDispositivoScreen(
    onVerificado: (User) -> Unit
) {
    val context = LocalContext.current
    val viewModel: AccesoViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val deviceId = remember { DeviceIdManager.getDeviceId(context) }

    LaunchedEffect(Unit) {
        viewModel.verificarDispositivo(deviceId)
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Gestor360°", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Verifica este dispositivo para continuar", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(24.dp))

                Text("ID del dispositivo:", style = MaterialTheme.typography.labelMedium)
                Text(deviceId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    uiState.isLoading -> CircularProgressIndicator()
                    uiState.error != null -> Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                    uiState.usuario != null -> {
                        LaunchedEffect(uiState.usuario) {
                            uiState.usuario?.let { onVerificado(it) }
                        }
                    }
                    else -> Button(onClick = { viewModel.verificarDispositivo(deviceId) }) {
                        Text("Verificar")
                    }
                }
            }
        }
    }
}
