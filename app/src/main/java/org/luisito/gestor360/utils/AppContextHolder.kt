package org.luisito.gestor360.utils

import android.content.Context

/**
 * Guarda el Application Context una sola vez (desde MainActivity.onCreate) para
 * que los repositorios puedan acceder a Room sin que cada ViewModel tenga que
 * pasarles el Context manualmente. Evita cambiar las firmas de los ViewModels
 * existentes (que hacen `ProductRepository()` sin parámetros).
 */
object AppContextHolder {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }
}
