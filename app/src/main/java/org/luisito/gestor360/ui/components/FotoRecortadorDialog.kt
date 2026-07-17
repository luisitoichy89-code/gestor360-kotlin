package org.luisito.gestor360.ui.components

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.luisito.gestor360.utils.FotoUtils
import kotlin.math.max

@Composable
fun FotoRecortadorDialog(
    uri: Uri,
    onConfirmar: (ByteArray) -> Unit,
    onCancelar: () -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var bitmapOriginal by remember { mutableStateOf<Bitmap?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var procesando by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val viewportDp = 260.dp
    val viewportPx = with(density) { viewportDp.toPx() }

    LaunchedEffect(uri) {
        cargando = true
        bitmapOriginal = withContext(Dispatchers.IO) { FotoUtils.decodificarParaEdicion(context, uri) }
        cargando = false
        scale = 1f
        offset = Offset.Zero
        if (bitmapOriginal == null) onError()
    }

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancelar, enabled = !procesando) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color.White)
                    }
                    Text("Ajustar foto", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    IconButton(
                        enabled = bitmapOriginal != null && !procesando,
                        onClick = {
                            val bmp = bitmapOriginal ?: return@IconButton
                            procesando = true
                            scope.launch {
                                val bw = bmp.width.toFloat()
                                val bh = bmp.height.toFloat()
                                val baseScale = max(viewportPx / bw, viewportPx / bh)
                                val total = baseScale * scale
                                val cropSize = viewportPx / total
                                val cx = bw / 2f - offset.x / total
                                val cy = bh / 2f - offset.y / total
                                val left = (cx - cropSize / 2f).coerceIn(0f, (bw - cropSize).coerceAtLeast(0f))
                                val top = (cy - cropSize / 2f).coerceIn(0f, (bh - cropSize).coerceAtLeast(0f))
                                val region = Rect(
                                    left.toInt(),
                                    top.toInt(),
                                    (left + cropSize).toInt().coerceAtMost(bmp.width),
                                    (top + cropSize).toInt().coerceAtMost(bmp.height)
                                )
                                val bytes = withContext(Dispatchers.Default) {
                                    FotoUtils.recortarYComprimir(bmp, region)
                                }
                                procesando = false
                                if (bytes != null) onConfirmar(bytes) else onError()
                            }
                        }
                    ) {
                        if (procesando) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Confirmar", tint = Color.White)
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        cargando -> CircularProgressIndicator(color = Color.White)
                        bitmapOriginal != null -> {
                            val bmp = bitmapOriginal!!
                            Box(
                                modifier = Modifier
                                    .size(viewportDp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color.DarkGray)
                                    .pointerInput(bmp) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val bw = bmp.width.toFloat()
                                            val bh = bmp.height.toFloat()
                                            val baseScale = max(viewportPx / bw, viewportPx / bh)
                                            val nuevaScale = (scale * zoom).coerceIn(1f, 5f)
                                            val total = baseScale * nuevaScale
                                            val maxX = max(0f, (bw * total - viewportPx) / 2f)
                                            val maxY = max(0f, (bh * total - viewportPx) / 2f)
                                            scale = nuevaScale
                                            offset = Offset(
                                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                                (offset.y + pan.y).coerceIn(-maxY, maxY)
                                            )
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Editar foto de perfil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offset.x,
                                            translationY = offset.y
                                        )
                                )
                            }
                        }
                        else -> Text("No se pudo cargar la imagen", color = Color.White)
                    }
                }

                Text(
                    "Arrastrá y pellizcá para encuadrar",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                )
            }
        }
    }
}
