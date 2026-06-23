package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.luisito.gestor360.ui.screens.DashboardScreen
import org.luisito.gestor360.ui.screens.activation.ActivationScreen
import org.luisito.gestor360.ui.theme.Gestor360Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Gestor360App()
        }
    }
}

@Composable
fun Gestor360App() {
    var isLicensed by remember { mutableStateOf(false) }

    if (isLicensed) {
        DashboardScreen(
            onLogout = { isLicensed = false }
        )
    } else {
        ActivationScreen(
            onLicenseValid = { isLicensed = true }
        )
    }
}
