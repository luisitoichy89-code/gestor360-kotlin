package org.luisito.gestor360.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.ColumnOrder
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luisito.gestor360.data.models.License
import org.luisito.gestor360.data.models.LicenseStatus

class LicenseRepository(
    private val supabase: SupabaseClient
) {
    
    suspend fun verifyLicense(androidId: String): LicenseStatus {
        return withContext(Dispatchers.IO) {
            try {
                val result = supabase.from("licenses")
                    .select {
                        filter {
                            eq("android_id", androidId)
                        }
                    }
                    .decodeList<License>()
                
                if (result.isEmpty()) {
                    return@withContext LicenseStatus.Error("Licencia no encontrada")
                }
                
                val license = result.first()
                
                if (license.isActive) {
                    LicenseStatus.Active(license)
                } else {
                    LicenseStatus.Expired("Licencia expirada o inactiva")
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                LicenseStatus.Error("Error al verificar licencia: ${e.message}")
            }
        }
    }
}

data class License(
    val id: String,
    val android_id: String,
    val isActive: Boolean,
    val expiresAt: String? = null
)

sealed class LicenseStatus {
    data class Active(val license: License) : LicenseStatus()
    data class Pending(val message: String) : LicenseStatus()
    data class Expired(val message: String) : LicenseStatus()
    data class Error(val message: String) : LicenseStatus()
}
