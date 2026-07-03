package org.luisito.gestor360.data.models

import kotlinx.serialization.Serializable

/**
 * Una fila por producto vendido (no una fila por "ticket"), igual que el backend Flask:
 * si el carrito tiene 3 productos, se insertan 3 filas en "ventas" con el efectivo y la
 * transferencia repartidos proporcionalmente al peso de cada producto en el total.
 */
@Serializable
data class Sale(
    val id: Long,
    val producto_id: Long,
    val producto_nombre: String,
    val cantidad: Double,
    val precio_unit: Double,
    val total: Double,
    val metodo: String,
    val efectivo: Double,
    val transferencia: Double,
    val usuario_id: Long,
    val almacen_id: String,
    val cliente_ci: String? = null,
    val cliente_tel: String? = null,
    val cliente_nombre: String? = null,
    val created_at: String? = null
)

/** Métodos de pago soportados, iguales al backend Flask (cash / transfer / mixed). */
enum class MetodoPago(val valor: String, val etiqueta: String) {
    EFECTIVO("cash", "Efectivo"),
    TRANSFERENCIA("transfer", "Transferencia"),
    MIXTO("mixed", "Mixto")
}
