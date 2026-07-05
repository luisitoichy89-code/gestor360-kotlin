package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Ticket
import org.luisito.gestor360.data.models.TicketMensaje

class TicketRepository {
    suspend fun getTickets(androidId: String): Result<List<Ticket>> {
        return try {
            SupabaseClientProvider.client.postgrest
                .rpc("get_tickets", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Ticket>().let { Result.success(it) }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getMensajes(ticketId: Long): Result<List<TicketMensaje>> {
        return try {
            SupabaseClientProvider.client.postgrest
                .rpc("get_ticket_mensajes", buildJsonObject { put("p_ticket_id", ticketId) })
                .decodeList<TicketMensaje>().let { Result.success(it) }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun crearTicket(androidId: String, mensaje: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("crear_ticket", buildJsonObject {
                put("p_android_id", androidId); put("p_mensaje", mensaje)
            })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun responderTicket(androidId: String, ticketId: Long, mensaje: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc("responder_ticket", buildJsonObject {
                put("p_android_id", androidId); put("p_ticket_id", ticketId); put("p_mensaje", mensaje)
            })
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
