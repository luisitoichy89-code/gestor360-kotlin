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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
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

class TicketViewModel(private val repo: TicketRepository = TicketRepository()) : androidx.lifecycle.ViewModel() {
    private val _s = kotlinx.coroutines.flow.MutableStateFlow(TicketUiState())
    val uiState = _s as kotlinx.coroutines.flow.StateFlow<TicketUiState>
    private var androidId = ""

    fun cargarTickets(aid: String) { androidId = aid; viewModelScope.launch { _s.value = _s.value.copy(isLoading = true); repo.getTickets(aid).onSuccess { _s.value = _s.value.copy(isLoading = false, tickets = it) }.onFailure { _s.value = _s.value.copy(isLoading = false, error = it.message) } } }
    fun abrirTicket(ticket: Ticket) { _s.value = _s.value.copy(ticketSeleccionado = ticket); viewModelScope.launch { repo.getMensajes(ticket.id!!).onSuccess { _s.value = _s.value.copy(mensajes = it) } } }
    fun refrescarMensajes() { val t = _s.value.ticketSeleccionado ?: return; viewModelScope.launch { repo.getMensajes(t.id!!).onSuccess { _s.value = _s.value.copy(mensajes = it) } } }
    fun crearTicket(mensaje: String) { viewModelScope.launch { repo.crearTicket(androidId, mensaje).onSuccess { cargarTickets(androidId); _s.value = _s.value.copy(ticketSeleccionado = null) } } }
    fun responder(mensaje: String) { val t = _s.value.ticketSeleccionado ?: return; viewModelScope.launch { repo.responderTicket(androidId, t.id!!, mensaje).onSuccess { abrirTicket(t) } } }
    fun cerrar() { _s.value = TicketUiState() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketsClienteScreen(androidId: String, onBack: () -> Unit, vm: TicketViewModel = viewModel()) {
    val s by vm.uiState.collectAsState()
    LaunchedEffect(androidId) { vm.cargarTickets(androidId) }
    var mostrarCrear by remember { mutableStateOf(false) }

    LaunchedEffect(s.ticketSeleccionado?.id) { while (true) { delay(5000); vm.refrescarMensajes() } }

    Scaffold(topBar = { TopAppBar(title = { Text("Soporte") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }) }, floatingActionButton = { FloatingActionButton(onClick = { mostrarCrear = true }) { Icon(Icons.Default.Add, "Nuevo ticket") } }) { padding ->
        if (s.ticketSeleccionado != null) {
            val ticket = s.ticketSeleccionado; var nuevoMensaje by remember { mutableStateOf("") }; var sending by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(s.mensajes) { m ->
                        val usuario = ticket?.usuario_nombre; val esMio = usuario != null && m.autor == usuario
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (esMio) Alignment.End else Alignment.Start) {
                            Surface(color = if (esMio) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(12.dp)) { Text(m.autor, fontWeight = FontWeight.Bold); Text(m.mensaje); Text(m.created_at?.take(16)?.replace("T", " ") ?: "", style = MaterialTheme.typography.labelSmall) } }
                        }
                    }
                }
                Row(Modifier.padding(8.dp)) { OutlinedTextField(nuevoMensaje, { nuevoMensaje = it }, label = { Text("Mensaje") }, modifier = Modifier.weight(1f), singleLine = true); IconButton(enabled = !sending, onClick = { if (nuevoMensaje.isNotBlank()) { sending = true; vm.responder(nuevoMensaje); nuevoMensaje = ""; sending = false } }) { Icon(Icons.Default.Send, "Enviar") } }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) { when { s.isLoading -> CircularProgressIndicator(); s.error != null -> Text(s.error!!, color = MaterialTheme.colorScheme.error); s.tickets.isEmpty() -> Text("No hay tickets"); else -> LazyColumn { items(s.tickets) { t -> TicketItem(t) { vm.abrirTicket(t) } } } } }
        }
    }

    if (mostrarCrear) { var mensaje by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = { mostrarCrear = false }, title = { Text("Nuevo ticket") }, text = { OutlinedTextField(mensaje, { mensaje = it }, label = { Text("Describe el problema") }, minLines = 3) }, confirmButton = { TextButton(onClick = { if (mensaje.isNotBlank()) { vm.crearTicket(mensaje); mostrarCrear = false } }) { Text("Enviar") } }, dismissButton = { TextButton(onClick = { mostrarCrear = false }) { Text("Cancelar") } }) }
}

@Composable
private fun TicketItem(t: Ticket, onClick: () -> Unit) {
    val color = when (t.estado) { "pendiente" -> MaterialTheme.colorScheme.error; "en_revision" -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Ticket #${t.id}", fontWeight = FontWeight.Bold); Text(t.updated_at?.take(16)?.replace("T", " ") ?: "") }; Surface(color = color) { Text(t.estado.replace("_", " "), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onPrimary) } } }
}
