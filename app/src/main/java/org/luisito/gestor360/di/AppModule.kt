package org.luisito.gestor360.di

import org.luisito.gestor360.data.repository.AuthRepositoryImpl
import org.luisito.gestor360.data.repository.LicenseRepositoryImpl
import org.luisito.gestor360.data.repository.ProductRepositoryImpl
import org.luisito.gestor360.data.repository.SaleRepositoryImpl
import org.luisito.gestor360.data.repository.UserRepositoryImpl
import org.luisito.gestor360.domain.repository.IAuthRepository
import org.luisito.gestor360.domain.repository.ILicenseRepository
import org.luisito.gestor360.domain.repository.IProductRepository
import org.luisito.gestor360.domain.repository.ISaleRepository
import org.luisito.gestor360.domain.repository.IUserRepository

object AppModule {
    
    // Singleton instances
    private val authRepository: IAuthRepository by lazy { AuthRepositoryImpl() }
    private val productRepository: IProductRepository by lazy { ProductRepositoryImpl() }
    private val saleRepository: ISaleRepository by lazy { SaleRepositoryImpl() }
    private val licenseRepository: ILicenseRepository by lazy { LicenseRepositoryImpl() }
    private val userRepository: IUserRepository by lazy { UserRepositoryImpl() }
    
    // Getters para los repositorios
    fun provideAuthRepository(): IAuthRepository = authRepository
    fun provideProductRepository(): IProductRepository = productRepository
    fun provideSaleRepository(): ISaleRepository = saleRepository
    fun provideLicenseRepository(): ILicenseRepository = licenseRepository
    fun provideUserRepository(): IUserRepository = userRepository
}
