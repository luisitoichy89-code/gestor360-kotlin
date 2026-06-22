package org.luisito.gestor360

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.ui.theme.Gestor360Theme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

private fun installCrashLogger(context: android.content.Context) {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val texto = "CRASH en hilo '${thread.name}':\n\n$sw"
            val rutas = listOf(
                File(context.filesDir, "gestor360_crash.txt"),
                File(Environment.getExternalStorageDirectory(), "gestor360_crash.txt"),
                File("/sdcard/gestor360_crash.txt")
            )
            for (ruta in rutas) {
                try {
                    ruta.writeText(texto)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashLogger(this)
        super.onCreate(savedInstanceState)

        val crashFile = File(filesDir, "gestor360_crash.txt")
        val crashPrevio = if (crashFile.exists()) {
            val texto = crashFile.readText()
            crashFile.delete()
            texto
        } else null

        try {
            setContent {
                Gestor360Theme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (crashPrevio != null) {
                            CrashDisplayScreen(crashPrevio)
                        } else {
                            ConnectionTestScreen()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val texto = "CRASH en onCreate/setContent:\n\n$sw"
                File(filesDir, "gestor360_crash.txt").writeText(texto)
                try { File("/sdcard/gestor360_crash.txt").writeText(texto) } catch (_: Exception) { }
            } catch (_: Exception) { }
            throw e
        }
    }
}

@Composable
fun CrashDisplayScreen(crashText: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "⚠️ La app se cerró inesperadamente. Este es el error:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
        SelectionContainer {
            Text(
                text = crashText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun ConnectionTestScreen() {
    var status by remember { mutableStateOf("Conectando con Supabase...") }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SupabaseClientProvider.client
                }
                status = "✅ Conexión a Supabase inicializada correctamente.\n\nEsta es la Parte 1: solo confirmamos que Gradle compila y el SDK conecta. El login y las pantallas reales vienen en la Parte 2."
            } catch (e: Exception) {
                status = "❌ Error al conectar: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Gestor360°",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(12.dp))
        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(12.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
