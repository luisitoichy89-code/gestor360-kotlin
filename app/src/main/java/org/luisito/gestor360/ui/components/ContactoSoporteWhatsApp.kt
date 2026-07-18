package org.luisito.gestor360.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.luisito.gestor360.R

/**
 * Botón de contacto por WhatsApp para soporte/ventas. Se muestra tal cual en
 * la pantalla de verificación de dispositivo y en la de login por PIN, para
 * que el contacto siempre esté visible sin importar en qué paso esté el
 * usuario.
 *
 * A propósito el número NO se ve como texto (se pidió dejarlo "detrás" de la
 * etiqueta): la etiqueta visible es "@soporte y ventas" y el número solo
 * viaja dentro del link de WhatsApp que abre el botón. Que WhatsApp lo
 * muestre después de abrir el chat no importa, era la aclaración explícita.
 */
private const val NUMERO_WHATSAPP = "5353104191" // +53 53104191

@Composable
fun ContactoSoporteWhatsApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$NUMERO_WHATSAPP"))
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No tienes WhatsApp instalado", Toast.LENGTH_SHORT).show()
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_whatsapp),
            contentDescription = "Contactar por WhatsApp",
            tint = Color(0xFF25D366),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "@soporte y ventas",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
