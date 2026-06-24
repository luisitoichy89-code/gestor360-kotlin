package org.luisito.gestor360.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PaymentDialog(
    total: Double,
    onDismiss: () -> Unit,
    onConfirm: (metodo: String, efectivo: Double, transferencia: Double) -> Unit,
    isProcessing: Boolean
) {
    var metodo by remember { mutableStateOf("efectivo") }
    var efectivo by remember { mutableStateOf("") }
    var transferencia by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💰 Pago") },
        text = {
            Column(
                modifier = Modifier.width(300.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total: ${String.format("%.2f", total)} CUP",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = metodo == "efectivo",
                            onClick = { metodo = "efectivo" },
                            colors = RadioButtonDefaults.colors()
                        )
                        Text("💵 Efectivo")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = metodo == "transferencia",
                            onClick = { metodo = "transferencia" },
                            colors = RadioButtonDefaults.colors()
                        )
                        Text("📲 Transferencia")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = metodo == "mixto",
                            onClick = { metodo = "mixto" },
                            colors = RadioButtonDefaults.colors()
                        )
                        Text("💱 Mixto")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (metodo) {
                    "efectivo" -> {
                        OutlinedTextField(
                            value = efectivo,
                            onValueChange = { efectivo = it },
                            label = { Text("Monto recibido") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Cambio: ${String.format("%.2f", (efectivo.toDoubleOrNull() ?: 0.0) - total)} CUP",
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    }
                    "transferencia" -> {
                        Text("Total a transferir: ${String.format("%.2f", total)} CUP")
                    }
                    "mixto" -> {
                        OutlinedTextField(
                            value = efectivo,
                            onValueChange = { efectivo = it },
                            label = { Text("Efectivo") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = transferencia,
                            onValueChange = { transferencia = it },
                            label = { Text("Transferencia") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val ef = efectivo.toDoubleOrNull() ?: 0.0
                        val tr = transferencia.toDoubleOrNull() ?: 0.0
                        if (ef + tr > 0) {
                            Text(
                                text = "Restante: ${String.format("%.2f", total - ef - tr)} CUP",
                                color = if (ef + tr >= total) androidx.compose.material3.MaterialTheme.colorScheme.primary
                                       else androidx.compose.material3.MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ef = efectivo.toDoubleOrNull() ?: 0.0
                    val tr = transferencia.toDoubleOrNull() ?: 0.0
                    when (metodo) {
                        "efectivo" -> onConfirm(metodo, ef, 0.0)
                        "transferencia" -> onConfirm(metodo, 0.0, total)
                        "mixto" -> onConfirm(metodo, ef, tr)
                    }
                },
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text("Confirmar Pago")
                }
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
