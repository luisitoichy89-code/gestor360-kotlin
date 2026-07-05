package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TrazaUiState(
    val isLoading: Boolean = false,
    val trazas: List<Traza> = emptyList(),
    val fechaSeleccionada: LocalDate = LocalDate.now(),
    val error: String? = null
)

class TrazaViewModel(private val repository: TrazaRepository = TrazaRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(TrazaUiState())
    val uiState: StateFlow<TrazaUiState> = _uiState.asStateFlow()
    private var androidIdActual = ""

    fun cargar(androidId: String) {
        androidIdActual = androidId
        cargarConFecha(androidId, _uiState.value.fechaSeleccionada)
    }

    fun seleccionarFecha(fecha: LocalDate) {
        _uiState.value = _uiState.value.copy(fechaSeleccionada = fecha)
        if (androidIdActual.isNotBlank()) cargarConFecha(androidIdActual, fecha)
    }

    private fun cargarConFecha(androidId: String, fecha: LocalDate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getTrazas(androidId)
                .onSuccess { lista ->
                    val filtradas = lista.filter {
                        val fechaTraza = try { LocalDate.parse(it.created_at?.take(10)) } catch (e: Exception) { null }
                        fechaTraza == fecha
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, trazas = filtradas)
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun refrescar() { if (androidIdActual.isNotBlank()) cargar(androidIdActual) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrazasScreen(
    androidId: String,
    onBack: (() -> Unit)? = null,
    viewModel: TrazaViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mostrarDatePicker by remember { mutableStateOf(false) }
    val hoy = LocalDate.now()
    val fechaMinima = hoy.minusDays(30)
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    LaunchedEffect(androidId) { viewModel.cargar(androidId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de actividad") },
                navigationIcon = { if (onBack != null) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Volver") } } },
                actions = {
                    IconButton(onClick = { mostrarDatePicker = true }) { Icon(Icons.Default.CalendarToday, contentDescription = "Seleccionar fecha") }
                    IconButton(onClick = { viewModel.refrescar() }) { Icon(Icons.Default.Refresh, contentDescription = "Refrescar") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Día: ${uiState.fechaSeleccionada.format(formatter)}", style = MaterialTheme.typography.titleSmall)
            }
            Text("Se guardan los últimos 30 días; el resto se borra automáticamente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            when {
                uiState.isLoading -> EstadoCargando()
                uiState.error != null -> EstadoError(uiState.error ?: "Error desconocido") { viewModel.refrescar() }
                uiState.trazas.isEmpty() -> EstadoVacio("Sin actividad para el ${uiState.fechaSeleccionada.format(formatter)}")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.trazas, key = { it.id }) { traza ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(traza.accion, fontWeight = FontWeight.Bold)
                                    Text(traza.created_at?.take(16)?.replace("T", " ") ?: "", style = MaterialTheme.typography.bodySmall)
                                }
                                if (!traza.detalle.isNullOrBlank()) { Text(traza.detalle, style = MaterialTheme.typography.bodySmall) }
                                Text(traza.usuario_nombre ?: "usuario #${traza.usuario_id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.fechaSeleccionada.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { mostrarDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val fecha = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        if (!fecha.isBefore(fechaMinima) && !fecha.isAfter(hoy)) {
                            viewModel.seleccionarFecha(fecha)
                        }
                    }
                    mostrarDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarDatePicker = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
