package org.luisito.gestor360.data.models

/** Ítem del carrito de venta. Vive solo en memoria mientras se arma la venta. */
data class CartItem(
    val productId: Long,
    val nombre: String,
    val precio: Double,
    val cantidad: Double,
    val stockDisponible: Double
) {
    val subtotal: Double
        get() = precio * cantidad
}
