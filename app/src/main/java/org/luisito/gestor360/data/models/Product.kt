package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: Long,
    val nombre: String,
    val precio: Double,
    val stock: Double,
    val ubicacion: String? = null,
    val categoria: String? = null,
    val almacen_id: String? = null,    // legacy, se mantiene por compatibilidad con el RPC
    val local_id: Long? = null         // FK → locales.id, para aislar productos por local
)
