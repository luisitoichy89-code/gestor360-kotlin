package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.luisito.gestor360.data.model.CartItem
import org.luisito.gestor360.ui.components.TopBar

@Composable
fun CheckoutScreen(
    cartItems: List<CartItem>,
    total: Double,
    onConfirmSale: (String, Double, Double) -> Unit,
    onCancel: () -> Unit
) {
    var paymentMethod by remember { mutableStateOf<String?>(null) }
    var cashAmount by remember { mutableStateOf("") }
    var transferAmount by remember { mutableStateOf("") }
    var tarjetaSeleccionada by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopBar(
                title = "💰 Cobrar",
                onMenuClick = {} // Sin menú en checkout
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Resumen del carrito
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Total a cobrar",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$${total}",
                        fontSize = 28.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botones de método de pago
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { paymentMethod = "cash" },
                    modifier = Modifier.weight(1f),
                    colors = if (paymentMethod == "cash") 
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    else ButtonDefaults.buttonColors()
                ) {
                    Text("💵 Efectivo")
                }
                Button(
                    onClick = { paymentMethod = "transfer" },
                    modifier = Modifier.weight(1f),
                    colors = if (paymentMethod == "transfer") 
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    else ButtonDefaults.buttonColors()
                ) {
                    Text("📲 Transferencia")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { paymentMethod = "mixed" },
                modifier = Modifier.fillMaxWidth(),
                colors = if (paymentMethod == "mixed") 
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                else ButtonDefaults.buttonColors()
            ) {
                Text("💱 Mixto")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Inputs según método
            when (paymentMethod) {
                "cash" -> {
                    OutlinedTextField(
                        value = cashAmount,
                        onValueChange = { cashAmount = it },
                        label = { Text("Monto recibido") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("0.00") }
                    )
                    if (cashAmount.isNotBlank()) {
                        val received = cashAmount.toDoubleOrNull() ?: 0.0
                        val change = received - total
                        if (change >= 0) {
                            Text(
                                text = "Cambio: $${change}",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
                "transfer" -> {
                    Text(
                        text = "Selecciona la tarjeta destino",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // Selector de tarjetas (simulado)
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("💳 BANDEC - ****1234")
                            Text("💳 BPA - ****5678")
                            Button(
                                onClick = { tarjetaSeleccionada = "BANDEC - ****1234" },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("Seleccionar BANDEC")
                            }
                        }
                    }
                    Text(
                        text = "Total a transferir: $${total}",
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                "mixed" -> {
                    OutlinedTextField(
                        value = cashAmount,
                        onValueChange = { cashAmount = it },
                        label = { Text("Efectivo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("0.00") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = transferAmount,
                        onValueChange = { transferAmount = it },
                        label = { Text("Transferencia") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("0.00") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        when (paymentMethod) {
                            "cash" -> {
                                val received = cashAmount.toDoubleOrNull() ?: 0.0
                                if (received >= total) {
                                    onConfirmSale("cash", total, 0.0)
                                }
                            }
                            "transfer" -> {
                                onConfirmSale("transfer", 0.0, total)
                            }
                            "mixed" -> {
                                val cash = cashAmount.toDoubleOrNull() ?: 0.0
                                val transfer = transferAmount.toDoubleOrNull() ?: 0.0
                                if (cash + transfer == total) {
                                    onConfirmSale("mixed", cash, transfer)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = paymentMethod != null
                ) {
                    Text("Confirmar")
                }
            }
        }
    }
}
