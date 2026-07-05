package org.luisito.gestor360.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import org.luisito.gestor360.BuildConfig
import org.luisito.gestor360.data.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch

@Composable
fun VerificarActualizacion() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mostrarDialogo by remember { mutableStateOf(false) }
    var urlDescarga by remember { mutableStateOf("") }
    var versionMinima by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val v = SupabaseClientProvider.client.postgrest
                    .rpc("get_configuracion", buildJsonObject { put("p_clave", "version_minima") })
                    .decodeAs<String>()
                versionMinima = v
                if (compararVersiones(BuildConfig.VERSION_NAME, v) < 0) {
                    val url = SupabaseClientProvider.client.postgrest
                        .rpc("get_configuracion", buildJsonObject { put("p_clave", "url_descarga") })
                        .decodeAs<String>()
                    urlDescarga = url
                    mostrarDialogo = true
                }
            } catch (_: Exception) {}
        }
    }

    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            title = { Text("🔄 Actualización disponible") },
            text = {
                Text("Hay una nueva versión de Gestor360.\n\nTu versión: ${BuildConfig.VERSION_NAME}\nNueva versión: $versionMinima")
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlDescarga)))
                    mostrarDialogo = false
                }) { Text("Descargar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { mostrarDialogo = false }) { Text("Ahora no") } }
        )
    }
}

fun compararVersiones(v1: String, v2: String): Int {
    val partes1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
    val partes2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(partes1.size, partes2.size)
    for (i in 0 until maxLen) {
        val a = partes1.getOrElse(i) { 0 }
        val b = partes2.getOrElse(i) { 0 }
        if (a != b) return a - b
    }
    return 0
}
