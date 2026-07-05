package org.luisito.gestor360.data.sync

import android.content.Context
import androidx.work.*
import org.luisito.gestor360.utils.SessionManager
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val androidId = SessionManager(applicationContext).getAndroidId()
        if (androidId.isBlank()) return Result.success() // nadie ha iniciado sesión todavía

        val resultado = SyncManager(applicationContext).sincronizar(androidId)
        return when {
            resultado.error == "Sin conexión" -> Result.retry()
            resultado.error != null -> Result.failure()
            else -> Result.success()
        }
    }

    companion object {
        private const val NOMBRE_TRABAJO_PERIODICO = "gestor360_sync_periodico"
        private const val NOMBRE_TRABAJO_MANUAL = "gestor360_sync_manual"

        private fun restricciones() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * De fondo, cada 15 minutos (mínimo que permite Android para trabajos
         * periódicos). Para reaccionar más rápido en cuanto vuelve la señal,
         * ver NetworkMonitor + sincronizarAhora() disparado desde la app.
         */
        fun programarPeriodico(context: Context) {
            val solicitud = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(restricciones())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NOMBRE_TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, solicitud
            )
        }

        /** Botón "Sincronizar ahora" o justo cuando NetworkMonitor detecta que volvió la señal. */
        fun sincronizarAhora(context: Context) {
            val solicitud = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(restricciones())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOMBRE_TRABAJO_MANUAL, ExistingWorkPolicy.REPLACE, solicitud
            )
        }
    }
}
