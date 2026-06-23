package org.luisito.gestor360.data.repository

import org.luisito.gestor360.data.models.LicenseStatus

class LicenseRepository {
    suspend fun checkLicense(deviceId: String): LicenseStatus {
        return try {
            // TODO: Implementar consulta a Supabase
            LicenseStatus.Pending
        } catch (e: Exception) {
            LicenseStatus.Error(e.message ?: "Error desconocido")
        }
    }
}
