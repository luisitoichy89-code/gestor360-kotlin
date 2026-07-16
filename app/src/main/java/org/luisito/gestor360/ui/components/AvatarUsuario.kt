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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import org.luisito.gestor360.utils.FotoUtils

/**
 * Avatar cuadrado del usuario. Un solo componente para dos usos:
 * - Dashboard (InicioTopBar): `editable = true`, mismo tamaño que el icono
 *   de cuenta que reemplaza; al tocarla abre el selector de imágenes de la
 *   galería (Android Photo Picker, sin permiso de almacenamiento) y entrega
 *   el ByteArray ya procesado (128x128, JPEG 70) vía onFotoSeleccionada.
 * - Login (PinLoginScreen): solo lectura, se arma con Surface propio ahí
 *   para heredar el color de bloqueado/no-bloqueado; ver rememberFotoBitmap.
 *
 * La foto nunca se sube a Supabase Storage: solo vive en Room (columna
 * `foto` de UserEntity) en este dispositivo.
 */
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
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val bytes = FotoUtils.procesarUriAFoto(context, uri)
            if (bytes != null) onFotoSeleccionada(bytes) else onError()
        }
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
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
}
