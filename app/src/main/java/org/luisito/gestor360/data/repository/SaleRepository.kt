package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Sale
import kotlinx.serialization.json.Json

class SaleRepository {

    suspend fun createSale(
        productoId: Int,
        productoNombre: String,
        cantidad: Double,
        precioUnit: Double,
        total: Double,
        metodo: String,
        efectivo: Double,
        transferencia: Double,
        usuarioId: Int,
        almacenId: String,
        clienteCi: String = "",
        clienteTel: String = "",
        clienteNombre: String = ""
    ): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            supabase.from("ventas").insert(
                mapOf(
                    "producto_id" to productoId,
                    "producto_nombre" to productoNombre,
                    "cantidad" to cantidad,
                    "precio_unit" to precioUnit,
                    "total" to total,
                    "metodo" to metodo,
                    "efectivo" to efectivo,
                    "transferencia" to transferencia,
                    "usuario_id" to usuarioId,
                    "almacen_id" to almacenId,
                    "cliente_ci" to clienteCi,
                    "cliente_tel" to clienteTel,
                    "cliente_nombre" to clienteNombre,
                    "created_at" to java.time.LocalDateTime.now().toString()
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getSalesByAlmacen(almacenId: String): List<Sale> {
        return try {
            val supabase = SupabaseClientProvider.client
            supabase.from("ventas")
                .select {
                    filter {
                        eq("almacen_id", almacenId)
                    }
                }
                .decodeAs<List<Sale>>()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
