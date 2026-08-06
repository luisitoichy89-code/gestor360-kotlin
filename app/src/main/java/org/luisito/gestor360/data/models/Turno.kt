package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Turno(
    val id: Long,
    val usuario_id: Long? = null,
    val apertura: Double,
    val cierre: Double? = null,
    val diferencia: Double? = null,
    val cliente_id: String? = null,
    val local_id: Long? = null,
    val created_at: String? = null,
    val numero_turno: Int = 0,
    val usuario_nombre: String? = null,
    val usuario_rol: String? = null
) {
    val estaAbierto: Boolean get() = cierre == null
}
