package org.luisito.gestor360.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, animationSpec = tween(800))

    LaunchedEffect(Unit) {
        visible = true
        delay(2000)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1E3A8A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gestor360°", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Sistema de gestión comercial", fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}
