package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.luisito.gestor360.data.model.User
import org.luisito.gestor360.ui.components.ScaffoldWithDrawer
import org.luisito.gestor360.ui.components.StatCard

@Composable
fun DashboardScreen(
    user: User,
    onVentasClick: () -> Unit = {},
    onProductosClick: () -> Unit = {},
    onMermaClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    ScaffoldWithDrawer(
        rol = user.rol,
        title = "Gestor360°",
        onItemClick = { route ->
            when (route) {
                "ventas" -> onVentasClick()
                "productos" -> onProductosClick()
                "merma" -> onMermaClick()
                // Agregar más rutas según sea necesario
            }
        },
        onLogout = onLogout
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "¡Bienvenido, ${user.nombre}!",
                fontSize = 24.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                text = "Resumen del día",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Estadísticas (4 tarjetas)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    listOf(
                        StatData("0.00", "Total CUP", "💰"),
                        StatData("0", "Ventas", "🛒"),
                        StatData("0.00", "Efectivo", "💵"),
                        StatData("0.00", "Transfer.", "📲")
                    )
                ) { stat ->
                    StatCard(
                        value = stat.value,
                        label = stat.label,
                        icon = stat.icon
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onVentasClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🛒 Ir a Ventas")
            }
        }
    }
}

data class StatData(
    val value: String,
    val label: String,
    val icon: String
)
