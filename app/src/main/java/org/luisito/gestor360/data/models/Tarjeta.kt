package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Tarjeta(
    val id: Long,
    val cliente_id: String,
    val almacen_id: String? = null,
    val banco: String,
    val numero: String,
    val titular: String? = null,
    val activo: Boolean = true,
    val created_at: String? = null
)
