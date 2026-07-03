package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

/**
 * Una merma propuesta por un vendedor. No descuenta stock hasta que el admin la aprueba
 * (ver MermaRepository.aprobar). Si el admin rechaza, el stock no se toca.
 */
@Serializable
data class MermaPendiente(
    val id: Long,
    val producto_id: Long,
    val producto_nombre: String,
    val cantidad: Double,
    val motivo: String? = null,
    val almacen_id: String,
    val cliente_id: String,
    val solicitado_por: Long,
    val solicitado_por_nombre: String? = null,
    val estado: String = "pendiente",
    val aprobado_por: Long? = null,
    val created_at: String? = null,
    val resuelto_at: String? = null
)
