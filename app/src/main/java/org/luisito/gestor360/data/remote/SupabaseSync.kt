package org.luisito.gestor360.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luisito.gestor360.data.model.Product
import org.luisito.gestor360.data.model.Sale

object SupabaseSync {

    suspend fun syncSale(sale: Sale, clienteId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.getClient().postgrest["ventas"]
                .insert(
                    mapOf(
                        "producto_id" to sale.productoId,
                        "producto_nombre" to sale.productoNombre,
                        "cantidad" to sale.cantidad,
                        "precio_unit" to sale.precioUnit,
                        "total" to sale.total,
                        "metodo" to sale.metodo,
                        "efectivo" to sale.efectivo,
                        "transferencia" to sale.transferencia,
                        "usuario_id" to sale.usuarioId,
                        "almacen_id" to sale.almacenId,
                        "cliente_ci" to sale.clienteCi,
                        "cliente_tel" to sale.clienteTel,
                        "cliente_nombre" to sale.clienteNombre,
                        "created_at" to sale.createdAt,
                        "cliente_id" to clienteId
                    )
                )
                .execute()
            response.statusCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    suspend fun syncProduct(product: Product, clienteId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.getClient().postgrest["productos"]
                .upsert(
                    mapOf(
                        "id" to product.id,
                        "nombre" to product.nombre,
                        "precio" to product.precio,
                        "stock" to product.stock,
                        "almacen_id" to product.almacenId,
                        "cliente_id" to clienteId
                    )
                )
                .execute()
            response.statusCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    suspend fun syncProductStock(productId: Long, newStock: Int, clienteId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseClient.getClient().postgrest["productos"]
                .update(
                    mapOf(
                        "stock" to newStock
                    )
                ) {
                    filter {
                        eq("id", productId)
                        eq("cliente_id", clienteId)
                    }
                }
                .execute()
            response.statusCode in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
