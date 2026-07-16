package org.luisito.gestor360.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.local.AppDatabase
import org.luisito.gestor360.data.local.entities.ConflictoEntity
import org.luisito.gestor360.data.sync.SyncManager

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.obtener(application)

    val pendientes: StateFlow<Int> = db.accionPendienteDao().observarCantidadPendiente()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val conflictos: StateFlow<List<ConflictoEntity>> = db.conflictoDao().observarPendientes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sincronizando = MutableStateFlow(false)
    val sincronizando: StateFlow<Boolean> = _sincronizando.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun sincronizarAhora(androidId: String) {
        if (_sincronizando.value || pendientes.value == 0) return
        viewModelScope.launch {
            _sincronizando.value = true
            val resultado = SyncManager(getApplication()).sincronizar(androidId)
            _mensaje.value = when {
                resultado.error != null ->
                    "No se pudo completar la sincronización. Verifica tu conexión e intenta de nuevo."
                resultado.exitosas == 0 && resultado.fallidas == 0 -> "Todo al día, nada pendiente"
                resultado.fallidas > 0 -> "Se sincronizaron ${resultado.exitosas}, pero ${resultado.fallidas} no se pudieron enviar. Se reintentará automáticamente."
                else -> "Sincronización completada: ${resultado.exitosas} cambios enviados"
            }
            _sincronizando.value = false
        }
    }

    fun resolverConflicto(id: Long) {
        viewModelScope.launch { db.conflictoDao().marcarResuelto(id) }
    }

    fun limpiarMensaje() { _mensaje.value = null }
}
