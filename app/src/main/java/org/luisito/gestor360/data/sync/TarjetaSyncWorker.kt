package com.gestor360.tarjetas.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gestor360.tarjetas.data.TarjetaRepository
import com.gestor360.tarjetas.data.remote.TarjetaRemoteDataSource
import java.util.concurrent.TimeUnit

class TarjetaSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: TarjetaRepository,
    private val remote: TarjetaRemoteDataSource,
    private val localIdActual: () -> Long
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "tarjeta_sync_work"
        const val MAX_INTENTOS = 8

        fun solicitar(): androidx.work.OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<TarjetaSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun encolar(workManager: WorkManager) {
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, solicitar())
        }
    }

    override suspend fun doWork(): Result {
        return try {
            push()
            pull()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= MAX_INTENTOS) Result.failure() else Result.retry()
        }
    }

    private suspend fun push() {
        val pendientes = repository.obtenerAccionesPendientes()
        for (accion in pendientes) {
            val resultado = remote.enviarAccion(accion)
            resultado.fold(
                onSuccess = { repository.marcarAccionCompletada(accion) },
                onFailure = { error ->
                    if (esErrorPermanente(error)) {
                        repository.marcarAccionFallida(accion, error.message)
                    } else {
                        throw error // Result.retry() con backoff exponencial
                    }
                }
            )
        }
    }

    /** Sin updated_at en Supabase: pull = traer todo lo del local y reemplazar lo ya sincronizado. */
    private suspend fun pull() {
        val localId = localIdActual()
        val remotas = remote.obtenerTodasDeLocal(localId)
        repository.reemplazarConDatosDeServidor(localId, remotas.map { it.toEntity() })
    }

    private fun esErrorPermanente(error: Throwable): Boolean {
        val msg = error.message ?: return false
        return msg.contains("400") || msg.contains("403") || msg.contains("422") || msg.contains("42501")
    }
}
