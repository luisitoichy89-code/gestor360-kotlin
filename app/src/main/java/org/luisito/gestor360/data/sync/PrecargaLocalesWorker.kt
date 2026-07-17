package org.luisito.gestor360.data.sync

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luisito.gestor360.data.repository.AprobacionStockRepository
import org.luisito.gestor360.data.repository.DevolucionRepository
import org.luisito.gestor360.data.repository.InventarioRepository
import org.luisito.gestor360.data.repository.LocalRepository
import org.luisito.gestor360.data.repository.MermaRepository
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.data.repository.TarjetaRepository
import org.luisito.gestor360.data.repository.TurnoRepository
import org.luisito.gestor360.utils.SessionManager
import java.util.concurrent.TimeUnit

/**
 * Precarga en segundo plano el caché Room de TODOS los locales a los que el
 * usuario (admin) tiene acceso — no solo el local activo — para que al
 * cambiar de local sin internet, el local 2, 3, etc. ya tengan datos, igual
 * que el local con el que se entró.
 *
 * Por qué WorkManager y no una simple corrutina:
 *  - Sobrevive a que la app se cierre o Android mate el proceso a mitad de
 *    la descarga, algo frecuente con la conexión inestable en Cuba.
 *  - Se encola con restricción de red (NetworkType.CONNECTED): si se llama
 *    sin internet, no falla ni se pierde — queda en espera y el propio
 *    Android lo ejecuta apenas vuelva la conexión, aunque sea en otra
 *    pantalla o con la app en segundo plano.
 *  - ExistingWorkPolicy.KEEP evita que se dispare la misma descarga pesada
 *    varias veces en paralelo (cuida los datos móviles).
 *  - Respeta un intervalo mínimo por local (SessionManager.getUltimaPrecarga)
 *    para no repetir descargas completas si ya se sincronizó hace poco.
 *
 * Qué precarga por local: productos, ventas, inventario del día, turno
 * activo, tarjetas, mermas y devoluciones pendientes, y ahora también
 * aprobaciones de stock pendientes.
 */
class PrecargaLocalesWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val NOMBRE_TRABAJO = "precarga_todos_los_locales"
        private const val INTERVALO_MINIMO_MS = 15 * 60 * 1000L // no repetir descarga completa del mismo local antes de 15 min

        /**
         * Encola la precarga de todos los locales. Segura de llamar seguido
         * (login, cambio de local, reconexión): si no hay internet o ya hay
         * una corriendo, no duplica trabajo ni gasta datos de más.
         */
        fun encolar(context: Context, forzar: Boolean = false) {
            val datos = Data.Builder().putBoolean("forzar", forzar).build()
            val restricciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val solicitud = OneTimeWorkRequestBuilder<PrecargaLocalesWorker>()
                .setConstraints(restricciones)
                .setInputData(datos)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOMBRE_TRABAJO,
                if (forzar) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                solicitud
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = SessionManager(applicationContext)
        val androidId = session.getAndroidId()
        if (androidId.isBlank()) return@withContext Result.success()

        val forzar = inputData.getBoolean("forzar", false)
        val localRepo = LocalRepository()
        val productRepo = ProductRepository(applicationContext)
        val saleRepo = SaleRepository(applicationContext, productRepo)
        val inventarioRepo = InventarioRepository(applicationContext)
        val turnoRepo = TurnoRepository(applicationContext)
        val tarjetaRepo = TarjetaRepository(applicationContext)
        val mermaRepo = MermaRepository(applicationContext)
        val devolucionRepo = DevolucionRepository(applicationContext)
        val aprobacionRepo = AprobacionStockRepository(applicationContext)

        val locales = localRepo.getLocales(androidId).getOrElse {
            return@withContext Result.retry()
        }

        var huboFallo = false
        for (local in locales) {
            val ultima = session.getUltimaPrecarga(local.id)
            val vencido = forzar || (System.currentTimeMillis() - ultima) > INTERVALO_MINIMO_MS
            if (!vencido) continue

            // Se hacen en secuencia (no en paralelo) para no saturar una conexión
            // móvil ya de por sí lenta con varias descargas simultáneas.
            val resultados = listOf(
                productRepo.precargarLocal(androidId, local.id),
                saleRepo.precargarLocal(androidId, local.id),
                inventarioRepo.precargarLocal(androidId, local.id),
                turnoRepo.precargarLocal(androidId, local.id),
                tarjetaRepo.precargarLocal(local.id),
                mermaRepo.precargarLocal(androidId, local.id),
                devolucionRepo.precargarLocal(androidId, local.id),
                aprobacionRepo.precargarLocal(androidId, local.id)
            )

            // Si products (lo mínimo para que el local sea usable) salió bien,
            // se marca como precargado aunque alguna fuente secundaria haya
            // fallado puntualmente — así no se re-descarga TODO por un solo
            // RPC que falló, solo para reintentar esa parte en la próxima vuelta.
            if (resultados.first().isSuccess) {
                session.setUltimaPrecarga(local.id, System.currentTimeMillis())
            }
            if (resultados.any { it.isFailure }) huboFallo = true
        }

        if (huboFallo) Result.retry() else Result.success()
    }
}
