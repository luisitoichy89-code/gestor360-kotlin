package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Tarjeta

class TarjetaRepository {

    suspend fun getTarjetas(androidId: String): Result<List<Tarjeta>> {
        return try {
            val tarjetas = SupabaseClientProvider.client.postgrest.rpc(
                "get_tarjetas", mapOf("p_android_id" to androidId)
            ).decodeList<Tarjeta>()
            Result.success(tarjetas)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun crearTarjeta(androidId: String, banco: String, numero: String, titular: String, almacenId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "crear_tarjeta",
                mapOf("p_android_id" to androidId, "p_banco" to banco, "p_numero" to numero, "p_titular" to titular, "p_almacen_id" to almacenId)
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
