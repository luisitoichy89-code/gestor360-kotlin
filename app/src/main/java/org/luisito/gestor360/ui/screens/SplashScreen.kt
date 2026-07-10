package org.luisito.gestor360.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.luisito.gestor360.R

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, animationSpec = tween(1000))

    LaunchedEffect(Unit) {
        visible = true
        delay(2000)
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFA3DBA9)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.gestor360_logo_bandera),
            contentDescription = "Gestor360",
            modifier = Modifier.size(180.dp),
            contentScale = ContentScale.Fit
        )
    }
}
