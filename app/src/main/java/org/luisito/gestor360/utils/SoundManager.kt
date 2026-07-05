package org.luisito.gestor360.utils

import android.content.Context
import android.media.RingtoneManager

object SoundManager {
    fun playVentaConfirmada(context: Context) {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notification)
            ringtone.play()
        } catch (_: Exception) {}
    }
}
