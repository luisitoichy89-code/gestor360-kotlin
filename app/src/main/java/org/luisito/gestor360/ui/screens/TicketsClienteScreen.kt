package org.luisito.gestor360.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.models.Ticket
import org.luisito.gestor360.data.models.TicketMensaje
import org.luisito.gestor360.data.repository.TicketRepository

data class TicketUiState(
    val isLoading: Boolean = false,
    val tickets: List<Ticket> = emptyList(),
    val mensajes: List<TicketMensaje> = emptyList(),
    val ticketSeleccionado: Ticket? = null,
    val error: String? = null
)

class TicketViewModel(private val repo: TicketRepository = TicketRepository()) : ViewModel() {
    private val _s = MutableStateFlow(TicketUiState()); val uiState: StateFlow<TicketUiState> = _s.asStateFlow()
    private var androidId = ""

    fun cargarTickets(aid: String) { androidId = aid; viewModelScope.launch { _s.value = _s.value.copy(isLoading = true); repo.getTickets(aid).onSuccess { _s.value = _s.value.copy(isLoading = false, tickets = it) }.onFailure { _s.value = _s.value.copy(isLoading = false, error = it.message) } } }
    fun abrirTicket(ticket: Ticket) { _s.value = _s.value.copy(ticketSeleccionado = ticket); viewModelScope.launch { repo.getMensajes(ticket.id!!).onSuccess { _s.value = _s.value.copy(mensajes = it) } } }
    fun refrescarMensajes() { _s.value.ticketSeleccionado?.let { t -> viewModelScope.launch { repo.getMensajes(t.id!!).onSuccess { _s.value = _s.value.copy(mensajes = it) } } } }
    fun crearTicket(telefono: String, mensaje: String) { viewModelScope.launch { repo.crearTicket(androidId, telefono, mensaje).onSuccess { cargarTickets(androidId); _s.value = _s.value.copy(ticketSeleccionado = null) } } }
    fun responder(mensaje: String) { val t = _s.value.ticketSeleccionado ?: return; viewModelScope.launch { repo.responderTicket(androidId, t.id!!, mensaje).onSuccess { abrirTicket(t) } } }
    fun cerrar() { _s.value = _s.value.copy(ticketSeleccionado = null, mensajes = emptyList()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsClienteScreen(androidId: String, onBack: () -> Unit, vm: TicketViewModel = viewModel()) {
    val s by vm.uiState.collectAsState()
    LaunchedEffect(androidId) { vm.cargarTickets(androidId) }
    var mostrarCrear by remember { mutableStateOf(false) }

    // Refrescar mensajes cada 5 segundos cuando hay ticket abierto
    LaunchedEffect(s.ticketSeleccionado) {
        if (s.ticketSeleccionado != null) {
            while (true) {
                delay(5000)
                vm.refrescarMensajes()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Soporte") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) }, floatingActionButton = { FloatingActionButton(onClick = { mostrarCrear = true }) { Icon(Icons.Default.Add, "Nuevo ticket") } }) { padding ->
        if (s.ticketSeleccionado != null) {
            var nuevoMensaje by remember { mutableStateOf("") }
            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(s.mensajes) { m ->
                        val esMio = m.autor == s.ticketSeleccionado!!.usuario_nombre
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (esMio) Alignment.End else Alignment.Start) {
                            Surface(color = if (esMio) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(m.autor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    Text(m.mensaje)
                                    Text(m.created_at?.take(16)?.replace("T", " ") ?: "", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.padding(8.dp)) {
                    OutlinedTextField(nuevoMensaje, { nuevoMensaje = it }, label = { Text("Mensaje") }, modifier = Modifier.weight(1f), singleLine = true)
                    IconButton(onClick = { if (nuevoMensaje.isNotBlank()) { vm.responder(nuevoMensaje); nuevoMensaje = "" } }) { Icon(Icons.Default.Send, "Enviar") }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                when { s.isLoading -> CircularProgressIndicator(); s.error != null -> Text(s.error!!, color = MaterialTheme.colorScheme.error); s.tickets.isEmpty() -> Text("No hay tickets"); else -> LazyColumn { items(s.tickets) { t -> TicketItem(t) { vm.abrirTicket(t) } } } }
            }
        }
    }

    if (mostrarCrear) {
        var telefono by remember { mutableStateOf("") }; var mensaje by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { mostrarCrear = false }, title = { Text("Nuevo ticket") }, text = { Column { OutlinedTextField(telefono, { telefono = it.filter { c -> c.isDigit() } }, label = { Text("Teléfono de contacto") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(mensaje, { mensaje = it }, label = { Text("Describe detalladamente el problema") }, minLines = 3) } }, confirmButton = { TextButton(onClick = { vm.crearTicket(telefono, mensaje); mostrarCrear = false }) { Text("Enviar") } }, dismissButton = { TextButton(onClick = { mostrarCrear = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun TicketItem(t: Ticket, onClick: () -> Unit) {
    val color = when(t.estado) { "pendiente" -> MaterialTheme.colorScheme.error; "en_revision" -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Ticket #${t.id}", fontWeight = FontWeight.Bold); Text(t.updated_at?.take(16)?.replace("T", " ") ?: "") }
            Surface(color = color, shape = MaterialTheme.shapes.small) { Text(t.estado.replace("_", " "), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}
