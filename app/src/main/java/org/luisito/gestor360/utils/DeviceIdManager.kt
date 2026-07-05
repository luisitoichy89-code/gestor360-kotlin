package org.luisito.gestor360.utils

import android.content.Context
import android.media.MediaDrm
import android.provider.Settings
import java.util.UUID

object DeviceIdManager {
    private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")

    fun getDeviceId(context: Context): String {
        return try {
            val mediaDrm = MediaDrm(WIDEVINE_UUID)
            val id = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            mediaDrm.close()
            id.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "FALLBACK"
        }
    }

    fun getFormattedDeviceId(context: Context): String {
        val raw = getDeviceId(context)
        return if (raw.length >= 8) "${raw.take(8)}-${raw.drop(8).take(4)}-${raw.drop(12).take(4)}" else raw
    }
}
