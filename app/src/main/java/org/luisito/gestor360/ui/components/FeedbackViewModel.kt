package org.luisito.gestor360.ui.components

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FeedbackState(
    val mensaje: String? = null,
    val tipo: FeedbackTipo = FeedbackTipo.EXITO
)

class FeedbackViewModel : ViewModel() {
    private val _state = MutableStateFlow(FeedbackState())
    val state: StateFlow<FeedbackState> = _state.asStateFlow()

    fun mostrar(mensaje: String, tipo: FeedbackTipo = FeedbackTipo.EXITO) {
        _state.value = FeedbackState(mensaje, tipo)
    }

    fun limpiar() {
        _state.value = FeedbackState()
    }
}
