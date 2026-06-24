package org.luisito.gestor360.data.repository

import org.luisito.gestor360.data.models.LicenseStatus
import org.luisito.gestor360.data.SupabaseClientProvider

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
                    val expDate = java.time.LocalDate.parse(expiracion)
                    val now = java.time.LocalDate.now()
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
            LicenseStatus.Error("Error: ${e.message}")
        }
    }
}
