package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.ui.screens.activation.ActivationScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContent {
                Gestor360App()
            }
        } catch (e: Exception) {
            // Escribir el error en un archivo visible
            val errorFile = java.io.File(filesDir, "gestor360_error.txt")
            errorFile.writeText("Error en onCreate: ${e.message}\n${e.stackTraceToString()}")
            finish()
        }
    }
}

@Composable
fun Gestor360App() {
    var isLicensed by remember { mutableStateOf(false) }

    Gestor360Theme {
        ActivationScreen(
            onLicenseValid = { isLicensed = true }
        )
    }
}
