package org.luisito.gestor360.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.luisito.gestor360.data.model.CartItem

class CartViewModel : ViewModel() {
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _total = MutableStateFlow(0.0)
    val total: StateFlow<Double> = _total.asStateFlow()

    fun addItem(item: CartItem) {
        viewModelScope.launch {
            val current = _cartItems.value.toMutableList()
            val existing = current.find { it.id == item.id }
            if (existing != null) {
                val newCantidad = existing.cantidad + item.cantidad
                if (newCantidad <= existing.stockDisponible) {
                    val index = current.indexOf(existing)
                    current[index] = existing.copy(cantidad = newCantidad)
                }
            } else {
                current.add(item)
            }
            _cartItems.value = current
            recalculateTotal()
        }
    }

    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            val current = _cartItems.value.toMutableList()
            current.removeAll { it.id == itemId }
            _cartItems.value = current
            recalculateTotal()
        }
    }

    fun updateQuantity(itemId: Long, newQuantity: Int) {
        viewModelScope.launch {
            val current = _cartItems.value.toMutableList()
            val index = current.indexOfFirst { it.id == itemId }
            if (index != -1) {
                val item = current[index]
                if (newQuantity in 1..item.stockDisponible) {
                    current[index] = item.copy(cantidad = newQuantity)
                    _cartItems.value = current
                    recalculateTotal()
                }
            }
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            _cartItems.value = emptyList()
            _total.value = 0.0
        }
    }

    private fun recalculateTotal() {
        _total.value = _cartItems.value.sumOf { it.subtotal }
    }
}
