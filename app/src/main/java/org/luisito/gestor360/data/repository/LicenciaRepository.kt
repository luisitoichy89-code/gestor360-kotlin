package org.luisito.gestor360.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luisito.gestor360.data.remote.SupabaseClient
import org.luisito.gestor360.utils.DeviceIdManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LicenciaRepository(private val context: Context) {

    suspend fun verificarLicencia(): LicenciaResult = withContext(Dispatchers.IO) {
        val deviceId = DeviceIdManager.getDeviceId(context)

        var remotaValida = false
        var clienteId: String? = null
        var expiracion: String? = null

        try {
            val response = SupabaseClient.getClient().postgrest["licencias"]
                .select {
                    filter {
                        eq("device_id", deviceId)
                        eq("activo", true)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()

            if (response.isNotEmpty()) {
                val lic = response.first()
                expiracion = lic["expiracion"] as? String
                clienteId = lic["cliente_id"] as? String

                if (expiracion != null) {
                    val expDate = LocalDate.parse(expiracion, DateTimeFormatter.ISO_LOCAL_DATE)
                    remotaValida = expDate >= LocalDate.now()
                } else {
                    remotaValida = true
                }
            }
        } catch (e: Exception) {
            // Sin conexión -> usamos validación local si existe
        }

        if (!remotaValida) {
            return@withContext LicenciaResult(
                valida = false,
                clienteId = null,
                expiracion = null,
                mensaje = "Licencia no válida o expirada"
            )
        }

        if (clienteId != null) {
            val prefs = context.getSharedPreferences("gestor360_config", Context.MODE_PRIVATE)
            prefs.edit().putString("cliente_id", clienteId).apply()
        }

        LicenciaResult(
            valida = true,
            clienteId = clienteId,
            expiracion = expiracion,
            mensaje = "Licencia válida"
        )
    }
}

data class LicenciaResult(
    val valida: Boolean,
    val clienteId: String?,
    val expiracion: String?,
    val mensaje: String
)
