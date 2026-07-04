package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Local(
    val id: Long,
    val nombre: String,
    val activo: Boolean = true,
    val cliente_id: String? = null
)
