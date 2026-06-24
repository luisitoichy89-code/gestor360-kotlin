package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.models.License
import org.luisito.gestor360.data.models.LicenseStatus
import org.luisito.gestor360.data.SupabaseClientProvider
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
                .decodeAs<List<License>>()

            if (result.isNotEmpty()) {
                val lic = result.first()
                if (!lic.activo) {
                    LicenseStatus.Error("Licencia inactiva")
                } else if (lic.expiracion != null) {
                    val expDate = LocalDate.parse(lic.expiracion)
                    val now = LocalDate.now()
                    if (expDate.isBefore(now)) {
                        LicenseStatus.Expired(lic.expiracion)
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
