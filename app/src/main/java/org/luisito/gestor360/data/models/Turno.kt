package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

/** Arqueo de caja: se abre con un monto inicial y se cierra contando el efectivo real. */
@Serializable
data class Turno(
    val id: Long,
    val usuario_id: Long? = null,
    val apertura: Double,
    val cierre: Double? = null,
    val diferencia: Double? = null,
    val created_at: String? = null
) {
    val estaAbierto: Boolean get() = cierre == null
}
