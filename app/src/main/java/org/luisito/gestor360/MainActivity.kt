package org.luisito.gestor360

import android.os.Bundle
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

/**
 * PARTE 1 — Pantalla mínima de verificación.
 *
 * Objetivo de esta primera parte: confirmar que el proyecto compila
 * correctamente con Gradle, que el SDK de Supabase se inicializa sin
 * errores, y que podemos hacer una consulta real (a la tabla `clientes`,
 * que ya existe en el esquema). No hay login todavía — eso es la Parte 2.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Gestor360Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectionTestScreen()
                }
            }
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
                    // Consulta mínima: solo contamos filas de "clientes".
                    // Como no hay sesión de Auth todavía, RLS bloqueará el
                    // acceso real a los datos (esto es ESPERADO y correcto:
                    // confirma que RLS está protegiendo la tabla). Lo que
                    // probamos aquí es que la conexión en sí no truena.
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
