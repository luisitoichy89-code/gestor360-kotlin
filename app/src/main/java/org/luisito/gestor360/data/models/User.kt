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
    val almacen_id: String? = "1",
    val local_id: Long? = null,   // FK → locales.id, null para admins con acceso multi-local
    val activo: Boolean = true,
    val created_at: String? = null
)
