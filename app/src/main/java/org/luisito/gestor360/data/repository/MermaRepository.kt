package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.MermaPendiente

/**
 * Flujo de aprobación de mermas: el vendedor propone (solicitar), queda en estado
 * "pendiente" sin tocar stock; el admin aprueba (descuenta stock real) o rechaza
 * (no descuenta, solo archiva el registro).
 */
class MermaRepository(
    private val productRepository: ProductRepository = ProductRepository()
) {

    suspend fun solicitar(
        productoId: Long,
        productoNombre: String,
        cantidad: Double,
        motivo: String,
        almacenId: String,
        clienteId: String,
        solicitadoPor: Long,
        solicitadoPorNombre: String
    ): Result<Unit> {
        return try {
            val payload = buildJsonObject {
                put("producto_id", productoId)
                put("producto_nombre", productoNombre)
                put("cantidad", cantidad)
                put("motivo", motivo)
                put("almacen_id", almacenId)
                put("cliente_id", clienteId)
                put("solicitado_por", solicitadoPor)
                put("solicitado_por_nombre", solicitadoPorNombre)
                put("estado", "pendiente")
            }
            SupabaseClientProvider.client.from("mermas_pendientes").insert(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPendientes(clienteId: String): Result<List<MermaPendiente>> {
        return try {
            val lista = SupabaseClientProvider.client
                .from("mermas_pendientes")
                .select {
                    filter {
                        eq("cliente_id", clienteId)
                        eq("estado", "pendiente")
                    }
                }
                .decodeList<MermaPendiente>()
            Result.success(lista)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Aprueba: descuenta el stock real del producto y marca la solicitud como resuelta. */
    suspend fun aprobar(merma: MermaPendiente, stockActualProducto: Double, aprobadoPor: Long): Result<Unit> {
        return try {
            productRepository.registrarMerma(merma.producto_id, stockActualProducto, merma.cantidad).getOrThrow()
            marcarResuelta(merma.id, "aprobada", aprobadoPor)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rechazar(merma: MermaPendiente, aprobadoPor: Long): Result<Unit> {
        return marcarResuelta(merma.id, "rechazada", aprobadoPor)
    }

    private suspend fun marcarResuelta(id: Long, estado: String, aprobadoPor: Long): Result<Unit> {
        return try {
            val payload = buildJsonObject {
                put("estado", estado)
                put("aprobado_por", aprobadoPor)
                put("resuelto_at", java.time.LocalDateTime.now().toString())
            }
            SupabaseClientProvider.client.from("mermas_pendientes").update(payload) {
                filter { eq("id", id) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
