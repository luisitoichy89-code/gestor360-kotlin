package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Local

/** RPC: get_locales. Por ahora es informativo (ver nota en el SQL). */
class LocalRepository {
    suspend fun getLocales(androidId: String): Result<List<Local>> {
        return try {
            val locales = SupabaseClientProvider.client.postgrest
                .rpc("get_locales", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Local>()
            Result.success(locales)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
