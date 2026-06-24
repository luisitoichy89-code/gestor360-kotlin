package org.luisito.gestor360.data.models

data class CartItem(
    val productId: Int,
    val nombre: String,
    val precio: Double,
    val cantidad: Double,
    val stockDisponible: Double
) {
    val subtotal: Double
        get() = precio * cantidad
}
