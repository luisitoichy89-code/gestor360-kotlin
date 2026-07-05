package org.luisito.gestor360.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.luisito.gestor360.R

class FCMService : FirebaseMessagingService() {
    override fun onMessageReceived(msg: RemoteMessage) {
        val titulo = msg.notification?.title ?: "Gestor360"
        val cuerpo = msg.notification?.body ?: ""
        val canalId = "gestor360_general"

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(canalId, "General", NotificationManager.IMPORTANCE_HIGH))
        }
        manager.notify(System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, canalId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(titulo)
                .setContentText(cuerpo)
                .setAutoCancel(true)
                .build()
        )
    }
}
