package org.luisito.gestor360.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EstadoChip(activo: Boolean, textoActivo: String = "Activo", textoInactivo: String = "Inactivo") {
    val bg = if (activo) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val fg = if (activo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(
            text = if (activo) textoActivo else textoInactivo,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun BuscadorField(query: String, onQueryChange: (String) -> Unit, placeholder: String = "Buscar...") {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                }
            }
        }
    )
}

@Composable
fun EstadoVacio(mensaje: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Inbox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(mensaje, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun EstadoError(mensaje: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(mensaje, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors()
            ) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
fun EstadoCargando() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ConfirmarEliminarDialog(nombre: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar") },
        text = { Text("¿Seguro que deseas eliminar \"$nombre\"? Esta acción no se puede deshacer.") },
        confirmButton = {
            TextButton(
                onClick = { onConfirm() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Cancelar") }
        }
    )
}

@Composable
fun PaginacionBar(pagina: Int, totalPaginas: Int, onPaginaAnterior: () -> Unit, onPaginaSiguiente: () -> Unit) {
    if (totalPaginas <= 1) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPaginaAnterior, enabled = pagina > 0) {
            Icon(Icons.Default.ArrowBack, "Anterior")
        }
        Text("${pagina + 1} / $totalPaginas", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onPaginaSiguiente, enabled = pagina < totalPaginas - 1) {
            Icon(Icons.Default.ArrowForward, "Siguiente")
        }
    }
}
