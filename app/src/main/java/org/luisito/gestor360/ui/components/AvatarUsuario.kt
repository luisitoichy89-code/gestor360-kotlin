package org.luisito.gestor360.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import android.net.Uri

@Composable
fun AvatarUsuario(
    fotoBytes: ByteArray?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(size / 4),
    editable: Boolean = false,
    onFotoSeleccionada: (ByteArray) -> Unit = {},
    onError: () -> Unit = {}
) {
    var uriParaRecortar by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uriParaRecortar = uri
    }

    val bitmap = rememberFotoBitmap(fotoBytes)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(
                if (editable) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button
                    ) {
                        launcher.launch("image/*")
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Foto de perfil", modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = "Foto de perfil",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    uriParaRecortar?.let { uri ->
        FotoRecortadorDialog(
            uri = uri,
            onConfirmar = { bytes ->
                uriParaRecortar = null
                onFotoSeleccionada(bytes)
            },
            onCancelar = { uriParaRecortar = null },
            onError = {
                uriParaRecortar = null
                onError()
            }
        )
    }
}
