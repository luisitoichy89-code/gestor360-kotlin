package org.luisito.gestor360.ui.screens.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.models.LicenseStatus
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.utils.DeviceIdManager

@Composable
fun ActivationScreen(
    onLicenseValid: () -> Unit,
    viewModel: ActivationViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var deviceId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        deviceId = DeviceIdManager.getFormattedDeviceId(context)
    }

    Gestor360Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🔑 Activación",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Comparte este ID con el administrador:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (deviceId.isEmpty()) {
                    CircularProgressIndicator()
                } else {
                    SelectionContainer {
                        Text(
                            text = deviceId,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Device ID", deviceId)
                            clipboard.setPrimaryClip(clip)
                        },
                        modifier = Modifier.width(200.dp)
                    ) {
                        Text("📋 Copiar ID")
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    when (val status = uiState.licenseStatus) {
                        is LicenseStatus.Active -> {
                            Text("✅ Licencia activa", color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onLicenseValid) {
                                Text("Continuar")
                            }
                        }
                        is LicenseStatus.Expired -> {
                            Text("⛔ Licencia expirada: ${status.date}", color = MaterialTheme.colorScheme.error)
                        }
                        is LicenseStatus.Pending -> {
                            Button(
                                onClick = { viewModel.checkLicense(deviceId) },
                                modifier = Modifier.width(200.dp)
                            ) {
                                if (uiState.isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                } else {
                                    Text("🔍 Verificar Licencia")
                                }
                            }
                        }
                        is LicenseStatus.Error -> {
                            Text("❌ ${status.message}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
