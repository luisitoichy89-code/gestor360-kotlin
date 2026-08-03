package org.luisito.gestor360.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Diálogo para que el admin registre una devolución directa desde Productos, sin pasar
 * por solicitud/aprobación (esa es la de [org.luisito.gestor360.ui.screens.DevolucionScreen]).
 *
 * Uso desde ProductosScreen, junto a las opciones existentes "Registrar merma" / "Editar":
 *
 *   var productoParaDevolucion by remember { mutableStateOf<Producto?>(null) }
 *   // ...en el menú de opciones del producto:
 *   DropdownMenuItem(text = { Text("Registrar devolución") }, onClick = { productoParaDevolucion = producto })
 *   // ...en el cuerpo del composable:
 *   productoParaDevolucion?.let { p ->
 *       DevolucionDirectaDialog(
 *           productoNombre = p.nombre,
 *           isSaving = devolucionVm.uiState.collectAsState().value.isSaving,
 *           onDismiss = { productoParaDevolucion = null },
 *           onConfirmar = { cantidad, destino, motivo ->
 *               devolucionVm.registrarDirecta(androidId, p.id, p.nombre, cantidad, destino, motivo) {
 *                   productoParaDevolucion = null
 *               }
 *           }
 *       )
 *   }
 */
@Composable
fun DevolucionDirectaDialog(
    productoNombre: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: (cantidad: Double, destino: String, motivo: String) -> Unit
) {
    var cantidadTexto by remember { mutableStateOf("1") }
    var motivo by remember { mutableStateOf("") }
    val cantidad = cantidadTexto.replace(",", ".").toDoubleOrNull()
    val cantidadValida = cantidad != null && cantidad > 0

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = RoundedCornerShape(18.dp),
        title = { Text("Devolución de \"$productoNombre\"", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Se registra de una vez, sin necesidad de aprobación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cantidadTexto,
                    onValueChange = { cantidadTexto = it },
                    label = { Text("Cantidad") },
                    isError = !cantidadValida,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text("Motivo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cantidadValida && !isSaving,
                onClick = { onConfirmar(cantidad!!, "stock", motivo) }
            ) { Text("Devolver a stock") }
        },
        dismissButton = {
            TextButton(
                enabled = cantidadValida && !isSaving,
                onClick = { onConfirmar(cantidad!!, "merma", motivo) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Marcar como merma (pérdida)") }
        }
    )
}
