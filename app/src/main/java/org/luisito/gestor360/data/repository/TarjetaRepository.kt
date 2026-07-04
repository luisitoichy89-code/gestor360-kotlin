package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.Tarjeta

/**
 * RPC: get_tarjetas, crear_tarjeta. No hay editar_tarjeta/eliminar_tarjeta/activar_tarjeta
 * en la lista que diste, así que dejo esos tres métodos apuntando también a nombres
 * supuestos ("editar_tarjeta", "eliminar_tarjeta", "activar_tarjeta") — coméntamelos o
 * dame los nombres reales si ya existen, o créalos si aún no.
 */
class TarjetaRepository(
    private val trazaRepository: TrazaRepository = TrazaRepository()
) {

    suspend fun getTarjetas(androidId: String): Result<List<Tarjeta>> {
        return try {
            val tarjetas = SupabaseClientProvider.client.postgrest
                .rpc("get_tarjetas", buildJsonObject { put("p_android_id", androidId) })
                .decodeList<Tarjeta>()
            Result.success(tarjetas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTarjetasActivas(androidId: String): Result<List<Tarjeta>> {
        return getTarjetas(androidId).map { lista -> lista.filter { it.activo } }
    }

    suspend fun crearTarjeta(androidId: String, banco: String, numero: String, titular: String): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_banco", banco)
                put("p_numero", numero)
                put("p_titular", titular)
            }
            SupabaseClientProvider.client.postgrest.rpc("crear_tarjeta", params)
            trazaRepository.registrar(androidId, "crear_tarjeta", "$banco $numero")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // NOTA: no estaba en tu lista de RPC confirmadas. Asumo este nombre; avísame el real.
    suspend fun editarTarjeta(androidId: String, id: Long, banco: String, numero: String, titular: String): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_id", id)
                put("p_banco", banco)
                put("p_numero", numero)
                put("p_titular", titular)
            }
            SupabaseClientProvider.client.postgrest.rpc("editar_tarjeta", params)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // NOTA: no estaba en tu lista de RPC confirmadas. Asumo este nombre; avísame el real.
    suspend fun setActivo(androidId: String, id: Long, activo: Boolean): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_id", id)
                put("p_activo", activo)
            }
            SupabaseClientProvider.client.postgrest.rpc("activar_tarjeta", params)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // NOTA: no estaba en tu lista de RPC confirmadas. Asumo este nombre; avísame el real.
    suspend fun eliminarTarjeta(androidId: String, id: Long): Result<Unit> {
        return try {
            val params = buildJsonObject {
                put("p_android_id", androidId)
                put("p_id", id)
            }
            SupabaseClientProvider.client.postgrest.rpc("eliminar_tarjeta", params)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
