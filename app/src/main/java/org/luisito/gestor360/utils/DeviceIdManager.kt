package org.luisito.gestor360.utils

import android.content.Context
import android.provider.Settings

object DeviceIdManager {
    private const val PREFS_NAME = "gestor360_device"
    private const val KEY_DEVICE_ID = "device_id"

    fun getDeviceId(context: Context): String {
        // 1. Obtener el Android ID (identificador único del dispositivo)
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""

        // 2. Si es válido, lo usamos como base
        if (androidId.isNotEmpty() && androidId != "9774d56d682e549c") {
            return "G360-$androidId"
        }

        // 3. Fallback: UUID si el Android ID no es confiable
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = "G360-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12).uppercase()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }
}
