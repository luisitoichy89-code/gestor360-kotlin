package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val nombre: String,
    val precio: Double,
    val stock: Double,
    val ubicacion: String? = null,
    val categoria: String? = null,
    val local_id: Long? = null
)
