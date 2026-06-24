package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Sale(
    val id: Int,
    val producto_id: Int,
    val producto_nombre: String,
    val cantidad: Double,
    val precio_unit: Double,
    val total: Double,
    val metodo: String,
    val efectivo: Double,
    val transferencia: Double,
    val usuario_id: Int,
    val almacen_id: String,
    val cliente_ci: String? = null,
    val cliente_tel: String? = null,
    val cliente_nombre: String? = null,
    val created_at: String
)
