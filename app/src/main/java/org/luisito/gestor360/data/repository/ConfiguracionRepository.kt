package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import org.luisito.gestor360.data.SupabaseClientProvider

@Serializable
private data class ConfiguracionFila(val clave: String, val valor: String)

/** Tabla global (no depende de cliente_id): versión actual publicada, link de descarga, etc. */
class ConfiguracionRepository {
    suspend fun obtenerConfiguracion(): Result<Map<String, String>> {
        return try {
            val filas = SupabaseClientProvider.client.postgrest
                .rpc("obtener_configuracion")
                .decodeList<ConfiguracionFila>()
            Result.success(filas.associateBy({ it.clave }, { it.valor }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
