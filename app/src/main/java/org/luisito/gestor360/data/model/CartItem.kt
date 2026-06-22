package org.luisito.gestor360.data.model

data class CartItem(
    val id: Long,
    val nombre: String,
    val precio: Double,
    val cantidad: Int,
    val stockDisponible: Int
) {
    val subtotal: Double
        get() = precio * cantidad
}
