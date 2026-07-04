package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Turno(
    val id: Long? = null,
    val cliente_id: String? = null,
    val usuario_id: Long,
    val almacen_id: String? = null,
    val efectivo_inicial: Double = 0.0,
    val efectivo_final: Double = 0.0,
    val total_ventas: Double = 0.0,
    val total_efectivo: Double = 0.0,
    val total_transferencia: Double = 0.0,
    val diferencia: Double = 0.0,
    val abierto: Boolean = true,
    val apertura: String? = null,
    val cierre: String? = null,
    val created_at: String? = null
)
