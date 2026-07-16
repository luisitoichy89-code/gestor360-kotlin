package org.luisito.gestor360.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.luisito.gestor360.ui.theme.neuShadow

/**
 * Botón redondo ☀️ / 🌙 para alternar entre tema claro y oscuro. Muestra el
 * ícono del tema al que se puede cambiar... no, muestra el estado actual:
 * ☀️ cuando está en claro (tócalo para pasar a oscuro), 🌙 cuando está en
 * oscuro (tócalo para volver a claro).
 */
@Composable
fun BotonTema(temaOscuro: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .neuShadow(shape = CircleShape, elevation = 3.dp, blur = 6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(if (temaOscuro) "🌙" else "☀️", fontSize = 16.sp)
    }
}
