package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Traza
import org.luisito.gestor360.data.repository.TrazaRepository
import org.luisito.gestor360.ui.components.EstadoCargando
import org.luisito.gestor360.ui.components.EstadoError
import org.luisito.gestor360.ui.components.EstadoVacio

data class TrazaUiState(
    val isLoading: Boolean = false,
    val trazas: List<Traza> = emptyList(),
    val error: String? = null
)

class TrazaViewModel(private val repository: TrazaRepository = TrazaRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(TrazaUiState())
    val uiState: StateFlow<TrazaUiState> = _uiState.asStateFlow()
    private var androidIdActual = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getTrazas(androidId)
                .onSuccess { lista -> _uiState.value = _uiState.value.copy(isLoading = false, trazas = lista) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun refrescar() {
        if (androidIdActual.isNotBlank()) cargar(androidIdActual)
    }
}

/** Solo admin. Muestra las últimas 200 acciones; el servidor borra solo lo de más de 30 días. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrazasScreen(
    androidId: String,
    onBack: (() -> Unit)? = null,
    viewModel: TrazaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de actividad") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, contentDescription = "Refrescar") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Se guardan los últimos 30 días; el resto se borra automáticamente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                uiState.trazas.isEmpty() -> EstadoVacio("Sin actividad registrada todavía")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.trazas, key = { it.id }) { traza ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(traza.accion, fontWeight = FontWeight.Bold)
                                    Text(traza.created_at?.take(16)?.replace("T", " ") ?: "", style = MaterialTheme.typography.bodySmall)
                                }
                                if (!traza.detalle.isNullOrBlank()) {
                                    Text(traza.detalle, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    traza.usuario_nombre ?: "usuario #${traza.usuario_id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
