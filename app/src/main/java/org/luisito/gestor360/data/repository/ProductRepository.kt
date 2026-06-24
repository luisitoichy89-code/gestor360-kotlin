package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Product
import kotlinx.serialization.json.Json

class ProductRepository {

    suspend fun getProducts(almacenId: String): List<Product> {
        return try {
            val supabase = SupabaseClientProvider.client
            supabase.from("productos")
                .select {
                    filter {
                        eq("almacen_id", almacenId)
                    }
                }
                .decodeAs<List<Product>>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createProduct(
        nombre: String,
        precio: Double,
        stock: Double,
        almacenId: String
    ): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            supabase.from("productos").insert(
                mapOf(
                    "nombre" to nombre,
                    "precio" to precio,
                    "stock" to stock,
                    "almacen_id" to almacenId
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateProduct(
        id: Int,
        nombre: String,
        precio: Double,
        stock: Double
    ): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            supabase.from("productos")
                .update(
                    mapOf(
                        "nombre" to nombre,
                        "precio" to precio,
                        "stock" to stock
                    )
                ) {
                    filter {
                        eq("id", id)
                    }
                }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteProduct(id: Int): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            supabase.from("productos")
                .delete {
                    filter {
                        eq("id", id)
                    }
                }
            true
        } catch (e: Exception) {
            false
        }
    }
}
