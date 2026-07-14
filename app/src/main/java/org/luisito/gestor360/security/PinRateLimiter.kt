package org.luisito.gestor360.security

import android.content.Context

/**
 * Antes, el límite de 3 intentos de PIN vivía SOLO en memoria de la pantalla
 * (un `remember` de Compose en PinLoginScreen): cerrar y volver a abrir la
 * app, o simplemente rotar la pantalla en algunos casos, reseteaba el
 * contador a cero. Un vendedor que quisiera adivinar el PIN de otro usuario
 * (o el de un admin) tenía intentos ilimitados en la práctica, solo con
 * paciencia para cerrar/abrir la app entre tandas de 3.
 *
 * Esto persiste el contador de intentos fallidos y la hora de bloqueo por
 * usuario (androidId) en SharedPreferences cifradas, y aplica backoff
 * exponencial: el bloqueo se hace más largo con cada racha de fallos, hasta
 * un tope. Sobrevive a cerrar la app, reiniciar el teléfono, etc.
 */
class PinRateLimiter(context: Context) {
    private val prefs = EncryptedPrefs.abrir(context, "gestor360_pin_rate_limit")

    companion object {
        private const val MAX_INTENTOS_ANTES_DE_BLOQUEAR = 3
        // Backoff: 30s, 2min, 10min, 30min... tope en 30min para no dejar el
        // dispositivo inutilizable por error humano legítimo (ej. admin que
        // olvidó cuál era el PIN nuevo).
        private val ESCALON_BLOQUEO_MS = longArrayOf(
            30_000L, 120_000L, 600_000L, 1_800_000L
        )
    }

    data class Estado(
        val bloqueado: Boolean,
        val intentosFallidos: Int,
        val segundosRestantes: Long = 0L
    )

    private fun keyIntentos(usuarioKey: String) = "intentos_$usuarioKey"
    private fun keyBloqueadoHasta(usuarioKey: String) = "bloqueado_hasta_$usuarioKey"

    fun estadoActual(usuarioKey: String): Estado {
        val intentos = prefs.getInt(keyIntentos(usuarioKey), 0)
        val bloqueadoHasta = prefs.getLong(keyBloqueadoHasta(usuarioKey), 0L)
        val ahora = System.currentTimeMillis()
        return if (bloqueadoHasta > ahora) {
            Estado(bloqueado = true, intentosFallidos = intentos, segundosRestantes = (bloqueadoHasta - ahora) / 1000)
        } else {
            Estado(bloqueado = false, intentosFallidos = intentos)
        }
    }

    /** Llamar tras un PIN incorrecto. Devuelve el nuevo estado (puede quedar bloqueado). */
    fun registrarFallo(usuarioKey: String): Estado {
        val intentosPrevios = prefs.getInt(keyIntentos(usuarioKey), 0)
        val intentos = intentosPrevios + 1
        val editor = prefs.edit().putInt(keyIntentos(usuarioKey), intentos)

        if (intentos >= MAX_INTENTOS_ANTES_DE_BLOQUEAR) {
            val racha = (intentos - MAX_INTENTOS_ANTES_DE_BLOQUEAR).coerceIn(0, ESCALON_BLOQUEO_MS.lastIndex)
            val duracion = ESCALON_BLOQUEO_MS[racha]
            editor.putLong(keyBloqueadoHasta(usuarioKey), System.currentTimeMillis() + duracion)
        }
        editor.apply()
        return estadoActual(usuarioKey)
    }

    /** Llamar tras un PIN correcto: limpia el historial de fallos. */
    fun registrarExito(usuarioKey: String) {
        prefs.edit().remove(keyIntentos(usuarioKey)).remove(keyBloqueadoHasta(usuarioKey)).apply()
    }
}
