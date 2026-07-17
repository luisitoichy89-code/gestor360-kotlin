package org.luisito.gestor360.data.sync

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider

object SyncReporter {
    suspend fun reportar(androidId: String, localId: Long, tipo: String, payload: JsonObject) {
        try {
            SupabaseClientProvider.client.postgrest.rpc(
                "reportar_sync",
                buildJsonObject {
                    put("p_android_id", androidId)
                    put("p_local_id", localId)
                    put("p_tipo", tipo)
                    put("p_payload", payload)
                }
            )
        } catch (_: Exception) { }
    }

    suspend fun reportarError(androidId: String, localId: Long, tipo: String, mensaje: String) {
        try {
            SupabaseClientProvider.client.postgrest.rpc(
                "reportar_sync_error",
                buildJsonObject {
                    put("p_android_id", androidId)
                    put("p_local_id", localId)
                    put("p_tipo", tipo)
                    put("p_error", mensaje)
                }
            )
        } catch (_: Exception) { }
    }
}
