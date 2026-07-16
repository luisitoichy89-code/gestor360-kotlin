package org.luisito.gestor360.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class FeedbackTipo(val color: Color) {
    EXITO(Color(0xFF4CAF50)),
    ERROR(Color(0xFFF44336)),
    PENDIENTE(Color(0xFFFF9800))
}

@Composable
fun FeedbackBar(
    mensaje: String?,
    tipo: FeedbackTipo = FeedbackTipo.EXITO,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = mensaje != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        LaunchedEffect(mensaje) {
            delay(2500)
            onDismiss()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mensaje ?: "",
                color = tipo.color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
