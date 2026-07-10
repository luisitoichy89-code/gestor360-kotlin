package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Long,
    val auth_id: String? = null,
    val cliente_id: String,
    val username: String,
    val nombre: String? = null,
    val rol: String,
    val pin: String? = null,
    val android_id: String? = null,
    /**
     * Local "hogar" del usuario. Para rol "seller" SIEMPRE debe venir con
     * valor (un vendedor pertenece a un único local). Para rol "admin" puede
     * venir null, lo que significa "tiene acceso a todos los locales del
     * cliente_id" — en ese caso el local activo se resuelve con el selector
     * de local (LocalSeleccionViewModel) y no hay default silencioso.
     */
    val local_id: Long? = null,
    val activo: Boolean = true,
    val created_at: String? = null
)
