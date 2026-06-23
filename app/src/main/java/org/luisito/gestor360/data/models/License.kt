package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class License(
    val id: String,
    val device_id: String,
    val almacen_id: String? = null,
    val expiracion: String? = null,
    val activo: Boolean = false,
    val cliente_id: String? = null
)
