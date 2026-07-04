package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Tarjeta

/**
 * Cuentas/tarjetas destino para cobrar por transferencia. Solo el admin las
 * gestiona (crear/editar/eliminar); el vendedor solo las lee para elegir una al cobrar.
 */
class TarjetaRepository {

    suspend fun getTarjetas(clienteId: String): Result<List<Tarjeta>> {
        return try {
            val tarjetas = SupabaseClientProvider.client
                .from("tarjetas")
                .select { filter { eq("cliente_id", clienteId) } }
                .decodeList<Tarjeta>()
            Result.success(tarjetas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTarjetasActivas(clienteId: String): Result<List<Tarjeta>> {
        return try {
            val tarjetas = SupabaseClientProvider.client
                .from("tarjetas")
                .select {
                    filter {
                        eq("cliente_id", clienteId)
                        eq("activo", true)
                    }
                }
                .decodeList<Tarjeta>()
            Result.success(tarjetas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun crearTarjeta(clienteId: String, banco: String, numero: String, titular: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("tarjetas").insert(
                mapOf(
                    "cliente_id" to clienteId,
                    "banco" to banco,
                    "numero" to numero,
                    "titular" to titular
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editarTarjeta(id: Long, banco: String, numero: String, titular: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("tarjetas").update(
                mapOf(
                    "banco" to banco,
                    "numero" to numero,
                    "titular" to titular
                )
            ) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setActivo(id: Long, activo: Boolean): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("tarjetas")
                .update(mapOf("activo" to activo)) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminarTarjeta(id: Long): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("tarjetas").delete { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
