package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: Long,
    val nombre: String,
    val precio: Double,
    val stock: Double,
    val almacen_id: String
)
