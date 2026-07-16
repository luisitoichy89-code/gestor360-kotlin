package org.luisito.gestor360.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object FeedbackSnackbar {
    private val verdeExito = Color(0xFF2E7D32)
    private val rojoError = Color(0xFFC62828)
    private val naranjaPendiente = Color(0xFFEF6C00)

    fun mostrar(
        scope: CoroutineScope,
        hostState: SnackbarHostState,
        mensaje: String,
        tipo: Tipo = Tipo.EXITO
    ) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(
                message = mensaje,
                actionLabel = "OK",
                duration = when (tipo) {
                    Tipo.EXITO -> androidx.compose.material3.SnackbarDuration.Short
                    Tipo.ERROR -> androidx.compose.material3.SnackbarDuration.Long
                    Tipo.PENDIENTE -> androidx.compose.material3.SnackbarDuration.Short
                }
            )
        }
    }

    enum class Tipo { EXITO, ERROR, PENDIENTE }
}
