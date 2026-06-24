package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClient
import org.luisito.gestor360.data.models.License
import org.luisito.gestor360.domain.repository.ILicenseRepository
import org.luisito.gestor360.domain.result.Result

class LicenseRepositoryImpl : ILicenseRepository {
    private val supabase = SupabaseClient.instance

    override suspend fun verifyLicense(androidId: String): Result<License> {
        return try {
            val licenses = supabase.from("licenses")
                .select { filter { eq("android_id", androidId) } }
                .decodeList<License>()
            
            val license = licenses.firstOrNull()
            if (license != null) Result.Success(license)
            else Result.Error("Licencia no encontrada")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al verificar licencia", e)
        }
    }

    override suspend fun activateLicense(androidId: String): Result<Unit> {
        return try {
            // Crear licencia nueva
            val newLicense = mapOf(
                "android_id" to androidId,
                "isActive" to true,
                "created_at" to System.currentTimeMillis()
            )
            supabase.from("licenses").insert(newLicense)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al activar licencia", e)
        }
    }

    override suspend fun getLicenseStatus(): Result<License> {
        return try {
            val androidId = android.provider.Settings.Secure.ANDROID_ID
            verifyLicense(androidId)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Error al obtener estado", e)
        }
    }
}
