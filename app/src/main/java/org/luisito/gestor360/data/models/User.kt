package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val auth_id: String,
    val username: String,
    val nombre: String? = null,
    val rol: String,
    val almacen_id: String,
    val activo: Boolean
)
