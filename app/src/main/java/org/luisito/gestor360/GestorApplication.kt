package org.luisito.gestor360

import android.app.Application
import org.luisito.gestor360.di.AppModule

class GestorApplication : Application() {
    
    companion object {
        lateinit var instance: GestorApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeDependencies()
    }
    
    private fun initializeDependencies() {
        // Forzar inicialización de módulo de dependencias
        AppModule.provideAuthRepository()
        AppModule.provideProductRepository()
        AppModule.provideSaleRepository()
        AppModule.provideLicenseRepository()
        AppModule.provideUserRepository()
    }
}
