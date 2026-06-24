package org.luisito.gestor360.data.repository

import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.LicenseStatus
import java.time.LocalDate

class LicenseRepository {

    suspend fun checkLicense(deviceId: String): LicenseStatus {
        return try {
            val supabase = SupabaseClientProvider.client
            val result = supabase.from("licencias")
                .select {
                    filter {
                        eq("device_id", deviceId)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()

            if (result.isNotEmpty()) {
                val lic = result.first()
                val activo = lic["activo"] as? Boolean ?: false
                val expiracion = lic["expiracion"] as? String

                if (!activo) {
                    LicenseStatus.Error("Licencia inactiva")
                } else if (expiracion != null) {
                    val expDate = LocalDate.parse(expiracion)
                    val now = LocalDate.now()
                    if (expDate.isBefore(now)) {
                        LicenseStatus.Expired(expiracion)
                    } else {
                        LicenseStatus.Active
                    }
                } else {
                    LicenseStatus.Active
                }
            } else {
                LicenseStatus.Pending
            }
        } catch (e: Exception) {
            LicenseStatus.Error(e.message ?: "Error al verificar licencia")
        }
    }
}
