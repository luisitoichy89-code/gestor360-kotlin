package org.luisito.gestor360.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.luisito.gestor360.BuildConfig
import org.luisito.gestor360.data.repository.ConfiguracionRepository

/**
 * Al arrancar, compara BuildConfig.VERSION_NAME contra "version_actual" en la
 * tabla configuracion. Si el servidor tiene una versión más nueva, muestra un
 * diálogo con link a GitHub Releases (o donde pongas la URL). No bloquea el
 * uso de la app si falla (sin internet, etc.) — es informativo, no obligatorio.
 */
@Composable
fun VerificarActualizacion() {
    var mostrarDialogo by remember { mutableStateOf(false) }
    var versionNueva by remember { mutableStateOf("") }
    var urlDescarga by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val config = ConfiguracionRepository().obtenerConfiguracion().getOrNull() ?: return@LaunchedEffect
        val versionRemota = config["version_actual"] ?: return@LaunchedEffect
        val url = config["url_descarga"] ?: return@LaunchedEffect

        if (esVersionMasNueva(versionRemota, BuildConfig.VERSION_NAME)) {
            versionNueva = versionRemota
            urlDescarga = url
            mostrarDialogo = true
        }
    }

    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { mostrarDialogo = false },
            title = { Text("Nueva versión disponible") },
            text = { Text("Hay una nueva versión de Gestor360 ($versionNueva). Descárgala para seguir recibiendo mejoras y correcciones.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlDescarga)))
                    mostrarDialogo = false
                }) { Text("Descargar") }
            },
            dismissButton = { TextButton(onClick = { mostrarDialogo = false }) { Text("Más tarde") } }
        )
    }
}

/** Compara versiones tipo "1.2.0" numéricamente, parte por parte (no como texto). */
private fun esVersionMasNueva(remota: String, local: String): Boolean {
    val r = remota.split(".").mapNotNull { it.trim().toIntOrNull() }
    val l = local.split(".").mapNotNull { it.trim().toIntOrNull() }
    for (i in 0 until maxOf(r.size, l.size)) {
        val ri = r.getOrElse(i) { 0 }
        val li = l.getOrElse(i) { 0 }
        if (ri != li) return ri > li
    }
    return false
}
