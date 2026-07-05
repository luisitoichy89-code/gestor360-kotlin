package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Ticket(
    val id: Long? = null,
    val cliente_id: String? = null,
    val usuario_id: Long? = null,
    val usuario_nombre: String? = null,
    val telefono_contacto: String? = null,
    val estado: String = "pendiente",
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class TicketMensaje(
    val id: Long? = null,
    val ticket_id: Long? = null,
    val autor: String = "",
    val mensaje: String = "",
    val created_at: String? = null
)
