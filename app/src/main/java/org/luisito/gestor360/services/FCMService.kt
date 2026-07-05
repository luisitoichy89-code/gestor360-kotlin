package org.luisito.gestor360.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.R
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.utils.SessionManager

class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(msg: RemoteMessage) {
        val titulo = msg.notification?.title ?: "Gestor360"
        val cuerpo = msg.notification?.body ?: ""
        val canalId = "gestor360_general"

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(canalId, "General", NotificationManager.IMPORTANCE_HIGH))
        }
        manager.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, canalId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(titulo)
                .setContentText(cuerpo)
                .setAutoCancel(true)
                .build()
        )
    }

    /**
     * Se llama cuando Firebase asigna (o renueva) el token de este dispositivo.
     * Sin esto, nunca hay forma de mandarle un push a un usuario específico:
     * el servidor necesita saber qué token corresponde a qué android_id.
     *
     * NECESITA una columna nueva. Corre esto en Supabase:
     *   alter table public.usuarios add column if not exists fcm_token text;
     *
     * Y este RPC (mismo patrón que el resto, resuelve el usuario por android_id):
     *   create or replace function public.actualizar_fcm_token(p_android_id text, p_token text)
     *   returns void language sql security definer as $$
     *     update usuarios set fcm_token = p_token where android_id = p_android_id;
     *   $$;
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val androidId = SessionManager(applicationContext).getAndroidId()
        if (androidId.isBlank()) return // todavía no ha iniciado sesión; se reintentará en el próximo login

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val params = buildJsonObject {
                    put("p_android_id", androidId)
                    put("p_token", token)
                }
                SupabaseClientProvider.client.postgrest.rpc("actualizar_fcm_token", params)
            } catch (_: Exception) {
                // Si falla, no pasa nada grave: el próximo onNewToken (o un refresco manual) lo reintenta.
            }
        }
    }
}
