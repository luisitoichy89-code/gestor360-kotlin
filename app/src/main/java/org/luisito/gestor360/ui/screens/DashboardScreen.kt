package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.luisito.gestor360.data.models.Local
import org.luisito.gestor360.ui.theme.Azul
import org.luisito.gestor360.ui.theme.Morado
import org.luisito.gestor360.ui.theme.TarjetaCarpeta
import org.luisito.gestor360.ui.viewmodels.LocalSeleccionViewModel
import org.luisito.gestor360.utils.AppContextHolder
import org.luisito.gestor360.utils.SessionManager

private data class SeccionDashboard(val titulo: String, val icono: ImageVector, val ruta: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userRol: String = "",
    username: String = "",
    androidId: String = "",
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    localSeleccionViewModel: LocalSeleccionViewModel? = null,
    onLocalCambiado: ((Local) -> Unit)? = null
) {
    val esAdmin = userRol == "admin"
    val context = AppContextHolder.context
    val sessionManager = remember { SessionManager(context) }

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

    // Toolbar secundario con local
    val localUiState by (localSeleccionViewModel?.uiState?.collectAsState() ?: remember { mutableStateOf(null) })
    val localActual = localUiState?.localSeleccionado
    var menuLocalAbierto by remember { mutableStateOf(false) }
    var localAConfirmar by remember { mutableStateOf<Local?>(null) }

    LaunchedEffect(androidId) {
        localSeleccionViewModel?.cargar(androidId)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Toolbar secundario
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        localActual?.nombre ?: "Sin local seleccionado",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (esAdmin && (localUiState?.locales?.size ?: 0) > 1) {
                        Text(
                            "Tocá para cambiar",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (esAdmin && (localUiState?.locales?.size ?: 0) > 1) {
                    Box(modifier = Modifier.clickable { menuLocalAbierto = true }) {
                        Icon(Icons.Default.ArrowDropDown, "Cambiar local", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = menuLocalAbierto, onDismissRequest = { menuLocalAbierto = false }) {
                        localUiState?.locales?.forEach { local ->
                            DropdownMenuItem(text = { Text(local.nombre) }, onClick = { localAConfirmar = local; menuLocalAbierto = false })
                        }
                    }
                }
            }
        }

        // Contenido principal
        if (secciones.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No hay secciones disponibles")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
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
    }

    // Diálogo de confirmación de cambio de local
    if (localAConfirmar != null) {
        AlertDialog(
            onDismissRequest = { localAConfirmar = null },
            title = { Text("Cambiar de local") },
            text = { Text("¿Cambiar a ${localAConfirmar!!.nombre}?") },
            confirmButton = {
                TextButton(onClick = {
                    val local = localAConfirmar!!
                    localSeleccionViewModel?.seleccionar(local)
                    sessionManager.setLocalId(local.id)
                    onLocalCambiado?.invoke(local)
                    localAConfirmar = null
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { localAConfirmar = null }) { Text("Cancelar") } }
        )
    }
}
