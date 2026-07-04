package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.ui.components.EstadoCargando
import org.luisito.gestor360.ui.components.EstadoError
import org.luisito.gestor360.ui.components.EstadoVacio
import org.luisito.gestor360.ui.viewmodels.CierreCajaViewModel
import org.luisito.gestor360.utils.CsvExporter

/**
 * Resumen del día: qué se vendió (agrupado por producto) y cuánto entró en
 * efectivo / transferencia / mixto. Cualquier admin o vendedor puede verlo;
 * no bloquea ni "cierra" ventas nuevas, es un reporte on-demand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CierreCajaScreen(
    androidId: String,
    onBack: (() -> Unit)? = null,
    viewModel: CierreCajaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cierre de caja") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, contentDescription = "Refrescar") }
                    IconButton(onClick = {
                        CsvExporter.exportarCierreCaja(
                            context,
                            uiState.fecha,
                            uiState.productosVendidos,
                            uiState.totalEfectivo,
                            uiState.totalTransferencia,
                            uiState.totalMixto
                        )
                    }) { Icon(Icons.Default.FileDownload, contentDescription = "Exportar CSV") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Resumen del ${uiState.fecha}", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                else -> {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            FilaResumen("Efectivo", uiState.totalEfectivo)
                            FilaResumen("Transferencia", uiState.totalTransferencia)
                            FilaResumen("Mixto", uiState.totalMixto)
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            FilaResumen("Total del día", uiState.totalGeneral, destacado = true)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Productos vendidos", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.productosVendidos.isEmpty()) {
                        EstadoVacio("No se registraron ventas hoy")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.productosVendidos) { (nombre, cantidad) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(nombre)
                                    Text(
                                        if (cantidad == cantidad.toLong().toDouble()) cantidad.toLong().toString() else cantidad.toString(),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaResumen(etiqueta: String, valor: Double, destacado: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(etiqueta, style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        Text(
            "$valor CUP",
            style = if (destacado) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (destacado) FontWeight.Bold else FontWeight.Normal,
            color = if (destacado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
