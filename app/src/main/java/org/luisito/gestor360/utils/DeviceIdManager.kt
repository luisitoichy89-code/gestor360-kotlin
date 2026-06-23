package org.luisito.gestor360.utils

import android.content.Context
import android.provider.Settings

object DeviceIdManager {
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN_DEVICE"
    }

    fun getFormattedDeviceId(context: Context): String {
        val raw = getDeviceId(context)
        return when (raw.length) {
            16 -> "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}"
            else -> raw
        }
    }
}
