package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider

/** RPC: contar_mensajes_no_leidos, marcar_ticket_leido. */
class TicketRepository {

    suspend fun contarNoLeidos(androidId: String): Result<Long> {
        return try {
            val cantidad = SupabaseClientProvider.client.postgrest
                .rpc("contar_mensajes_no_leidos", buildJsonObject { put("p_android_id", androidId) })
                .decodeAs<Long>()
            Result.success(cantidad)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun marcarLeido(androidId: String, ticketId: Long): Result<Unit> {
        return try {
            val params = buildJsonObject { put("p_android_id", androidId); put("p_ticket_id", ticketId) }
            SupabaseClientProvider.client.postgrest.rpc("marcar_ticket_leido", params)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
