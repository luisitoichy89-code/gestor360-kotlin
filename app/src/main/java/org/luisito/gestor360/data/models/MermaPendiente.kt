package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

/**
 * Una merma propuesta por un vendedor. No descuenta stock hasta que el admin la resuelve
 * como aprobada (el RPC resolver_merma hace el descuento server-side).
 */
@Serializable
data class MermaPendiente(
    val id: Long,
    val producto_id: String,
    val producto_nombre: String,
    val cantidad: Double,
    val motivo: String? = null,
    val solicitado_por: Long? = null,
    val solicitado_por_nombre: String? = null,
    val estado: String = "pendiente",
    val aprobado_por: Long? = null,
    val cliente_id: String? = null,
    val local_id: Long? = null,
    val created_at: String? = null,
    val resuelto_at: String? = null
)
