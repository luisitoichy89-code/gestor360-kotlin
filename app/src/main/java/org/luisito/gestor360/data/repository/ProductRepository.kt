package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClient
import org.luisito.gestor360.data.models.Product

class ProductRepository {
    private val supabase = SupabaseClient.instance
    
    suspend fun getProducts(): List<Product> {
        return try {
            supabase.from("products")
                .select()
                .decodeList<Product>()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun createProduct(product: Product): Boolean {
        return try {
            supabase.from("products")
                .insert(product)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun updateProduct(id: String, product: Product): Boolean {
        return try {
            supabase.from("products")
                .update(product) {
                    filter { eq("id", id) }
                }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun deleteProduct(id: String): Boolean {
        return try {
            supabase.from("products")
                .delete {
                    filter { eq("id", id) }
                }
            true
        } catch (e: Exception) {
            false
        }
    }
}
