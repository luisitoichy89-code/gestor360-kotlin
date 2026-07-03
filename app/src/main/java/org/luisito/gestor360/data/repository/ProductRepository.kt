package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Product

/**
 * IMPORTANTE: se usa buildJsonObject en vez de mapOf(...) para cualquier payload que
 * mezcle tipos (String + Double, por ejemplo). Un mapOf con tipos mixtos se infiere como
 * Map<String, Any>, y kotlinx.serialization no puede serializar "Any"
 * ("Serializer for class 'Any' is not found"). JsonObject sí tiene serializador propio.
 */
class ProductRepository {

    suspend fun getProducts(almacenId: String): Result<List<Product>> {
        return try {
            val productos = SupabaseClientProvider.client
                .from("productos")
                .select {
                    filter { eq("almacen_id", almacenId) }
                    order("nombre", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<Product>()
            Result.success(productos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchProducts(almacenId: String, query: String): Result<List<Product>> {
        return try {
            val productos = SupabaseClientProvider.client
                .from("productos")
                .select {
                    filter {
                        eq("almacen_id", almacenId)
                        if (query.isNotBlank()) ilike("nombre", "%$query%")
                    }
                    order("nombre", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    limit(30)
                }
                .decodeList<Product>()
            Result.success(productos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createProduct(nombre: String, precio: Double, stock: Double, almacenId: String): Result<Unit> {
        return try {
            val payload = buildJsonObject {
                put("nombre", nombre)
                put("precio", precio)
                put("stock", stock)
                put("almacen_id", almacenId)
            }
            SupabaseClientProvider.client.from("productos").insert(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Edición completa: nombre, precio y stock total en un solo guardado (para el diálogo de editar). */
    suspend fun updateProduct(id: Long, nombre: String, precio: Double, stock: Double): Result<Unit> {
        return try {
            val payload = buildJsonObject {
                put("nombre", nombre)
                put("precio", precio)
                put("stock", stock)
            }
            SupabaseClientProvider.client.from("productos").update(payload) {
                filter { eq("id", id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameProduct(id: Long, nuevoNombre: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("productos")
                .update(mapOf("nombre" to nuevoNombre)) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePrecio(id: Long, nuevoPrecio: Double): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("productos")
                .update(mapOf("precio" to nuevoPrecio)) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setStock(id: Long, nuevoStock: Double): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("productos")
                .update(mapOf("stock" to nuevoStock)) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Registra una merma (rotura, vencimiento, robo): reduce stock sin bajar de 0. */
    suspend fun registrarMerma(id: Long, stockActual: Double, cantidad: Double): Result<Unit> {
        val nuevoStock = (stockActual - cantidad).coerceAtLeast(0.0)
        return setStock(id, nuevoStock)
    }

    /** Descuenta stock por una venta (no baja de 0, igual que el backend Flask). */
    suspend fun descontarStock(id: Long, stockActual: Double, cantidadVendida: Double): Result<Unit> {
        val nuevoStock = (stockActual - cantidadVendida).coerceAtLeast(0.0)
        return setStock(id, nuevoStock)
    }

    suspend fun deleteProduct(id: Long): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("productos").delete { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
