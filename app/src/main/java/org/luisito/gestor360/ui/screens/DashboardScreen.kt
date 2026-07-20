package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.sync.SyncWorker
import org.luisito.gestor360.ui.theme.Azul
import org.luisito.gestor360.ui.theme.Morado
import org.luisito.gestor360.ui.theme.TarjetaCarpeta
import org.luisito.gestor360.utils.AppContextHolder

private data class SeccionDashboard(val titulo: String, val icono: ImageVector, val ruta: String, val color: Color)

private val VerdeActualizar = Color(0xFF2E7D32)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userRol: String = "",
    username: String = "",
    androidId: String = "",
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    val esAdmin = userRol == "admin"
    val context = AppContextHolder.context
    val scope = rememberCoroutineScope()
    var actualizando by remember { mutableStateOf(false) }

    val secciones = buildList {
        add(SeccionDashboard("Ventas", Icons.Default.PointOfSale, "ventas", Azul))
        add(SeccionDashboard("Productos", Icons.Default.Inventory2, "productos", Morado))
        add(SeccionDashboard("Inventario", Icons.Default.ReceiptLong, "inventario", Azul))
        if (esAdmin) {
            add(SeccionDashboard("Tarjetas", Icons.Default.CreditCard, "tarjetas", Morado))
            add(SeccionDashboard("Aprobaciones", Icons.Default.FactCheck, "aprobaciones", Azul))
            add(SeccionDashboard("Devolución", Icons.Default.AssignmentReturn, "devolucion", Morado))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (secciones.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay secciones disponibles")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(secciones) { seccion ->
                        TarjetaCarpeta(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            shape = RoundedCornerShape(22.dp),
                            elevation = 3.dp,
                            accentColor = seccion.color,
                            accentThickness = 9.dp,
                            onClick = { onNavigate(seccion.ruta) }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(seccion.color.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(seccion.icono, null, modifier = Modifier.size(26.dp), tint = seccion.color)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(seccion.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Botón "Actualizar todo" pegado abajo
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                color = Color.Transparent
            ) {
                Button(
                    onClick = {
                        actualizando = true
                        scope.launch {
                            SyncWorker.sincronizarAhora(context)
                            actualizando = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VerdeActualizar),
                    enabled = !actualizando
                ) {
                    if (actualizando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Actualizando...", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Actualizar todo", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
