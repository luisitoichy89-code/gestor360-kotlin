package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Tarjeta

class TarjetaRepository {

    suspend fun getTarjetas(androidId: String): Result<List<Tarjeta>> {
        return try {
            val tarjetas = SupabaseClientProvider.client.postgrest
                .rpc("get_tarjetas", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Tarjeta>()
            Result.success(tarjetas)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun crearTarjeta(androidId: String, banco: String, numero: String, titular: String): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_banco", banco)
                put("p_numero", numero)
                put("p_titular", titular)
                put("p_almacen_id", androidId)
            }
            SupabaseClientProvider.client.postgrest.rpc("crear_tarjeta", params)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Estos métodos no tienen RPC, usan acceso directo (solo admin)
    suspend fun editarTarjeta(androidId: String, id: Long, banco: String, numero: String, titular: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("tarjetas")
                .update(mapOf("banco" to banco, "numero" to numero, "titular" to titular)) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setActivo(androidId: String, id: Long, activo: Boolean): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("tarjetas")
                .update(mapOf("activo" to activo)) { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun eliminarTarjeta(androidId: String, id: Long): Result<Unit> {
        return try {
            SupabaseClientProvider.client.from("tarjetas")
                .delete { filter { eq("id", id) } }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
