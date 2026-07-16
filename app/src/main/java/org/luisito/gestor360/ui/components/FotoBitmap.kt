package org.luisito.gestor360.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Decodifica el BLOB guardado en Room (JPEG 128x128) a ImageBitmap, o null si no hay foto. */
@Composable
fun rememberFotoBitmap(fotoBytes: ByteArray?): ImageBitmap? = remember(fotoBytes) {
    fotoBytes?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
}
