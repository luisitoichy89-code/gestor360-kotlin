package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

/** id es uuid en la tabla real; producto_id/usuario_id son bigint (FK a productos/usuarios). */
@Serializable
data class Sale(
    val id: String? = null,
    val producto_id: Long,
    val producto_nombre: String? = null,
    val cantidad: Double,
    val total: Double,
    val metodo: String,
    val efectivo: Double = 0.0,
    val transferencia: Double = 0.0,
    val usuario_id: Long? = null,
    val local_id: Long? = null,
    val cliente_ci: String? = null,
    val cliente_tel: String? = null,
    val cliente_nombre: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

enum class MetodoPago(val valor: String, val etiqueta: String) {
    EFECTIVO("cash", "Efectivo"),
    TRANSFERENCIA("transfer", "Transferencia"),
    MIXTO("mixed", "Mixto")
}
