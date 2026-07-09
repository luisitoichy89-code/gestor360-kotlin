package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Tarjeta(
    val id: Long,
    val banco: String,
    val numero: String,
    val titular: String? = null,
    val activo: Boolean = true,
    val cliente_id: String? = null,
    val local_id: Long? = null,
    val created_at: String? = null
)
