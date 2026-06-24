package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.luisito.gestor360.ui.theme.Gestor360Theme

@Composable
fun SyncScreen(
    onBack: () -> Unit,
    onSync: () -> Unit,
    isLoading: Boolean = false,
    result: String? = null
) {
    Gestor360Theme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🔄 Sincronización",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sincroniza tus datos con la nube",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = onSync,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Text("🔄 Sincronizar Ahora")
                    }
                }

                if (result != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = result,
                        color = if (result.startsWith("✅")) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text("← Volver")
                }
            }
        }
    }
}
