package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Traza(
    val id: Long,
    val usuario_id: Long? = null,
    val usuario_nombre: String? = null,
    val accion: String,
    val detalle: String? = null,
    val created_at: String? = null
)
