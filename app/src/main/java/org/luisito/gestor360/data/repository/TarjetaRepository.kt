package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Tarjeta

class TarjetaRepository {
    suspend fun getTarjetas(androidId: String): Result<List<Tarjeta>> {
        return try {
            val tarjetas = SupabaseClientProvider.client.postgrest.rpc(
                "get_tarjetas", buildJsonObject { put("p_android_id", androidId) }
            ).decodeList<Tarjeta>()
            Result.success(tarjetas)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun crearTarjeta(androidId: String, banco: String, numero: String, titular: String, almacenId: String): Result<Unit> {
        return try {
            SupabaseClientProvider.client.postgrest.rpc(
                "crear_tarjeta",
                buildJsonObject { put("p_android_id", androidId); put("p_banco", banco); put("p_numero", numero); put("p_titular", titular); put("p_almacen_id", almacenId) }
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }
}
