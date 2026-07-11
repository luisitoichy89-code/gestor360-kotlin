package org.luisito.gestor360.ui.util

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Traduce cualquier excepción a un mensaje breve y amigable en español,
 * pensado para mostrarse directo al usuario final (nada de stacktraces,
 * nombres de clases Kotlin/Java, ni mensajes en inglés de Supabase/Ktor).
 */
fun Throwable.mensajeAmigable(contexto: String = "Ocurrió un problema"): String {
    return when (this) {
        is UnknownHostException,
        is ConnectException ->
            "Sin conexión a internet. Verifica tu red e intenta de nuevo."
        is SocketTimeoutException ->
            "La conexión tardó demasiado en responder. Intenta de nuevo."
        is IOException ->
            "Hubo un problema de conexión. Verifica tu internet e intenta de nuevo."
        else -> {
            val detalle = message?.lowercase().orEmpty()
            when {
                detalle.contains("timeout") ->
                    "La conexión tardó demasiado en responder. Intenta de nuevo."
                detalle.contains("network") || detalle.contains("host") || detalle.contains("connect") ->
                    "Sin conexión a internet. Verifica tu red e intenta de nuevo."
                detalle.contains("unauthorized") || detalle.contains("403") || detalle.contains("401") ->
                    "No tienes permiso para realizar esta acción."
                detalle.contains("duplicate") || detalle.contains("unique") ->
                    "Ese registro ya existe."
                else -> "$contexto. Intenta de nuevo."
            }
        }
    }
}
