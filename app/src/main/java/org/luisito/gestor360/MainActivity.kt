package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.di.AppModule
import org.luisito.gestor360.ui.screens.navigation.AppNavHost
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.ui.viewmodels.AuthViewModel
import org.luisito.gestor360.ui.viewmodels.ProductsViewModel
import org.luisito.gestor360.ui.viewmodels.SalesViewModel
import org.luisito.gestor360.ui.viewmodels.ActivationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Gestor360Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppScreen()
                }
            }
        }
    }
    
    @Composable
    fun AppScreen() {
        // Inyectar dependencias
        val authRepo = AppModule.provideAuthRepository()
        val productRepo = AppModule.provideProductRepository()
        val saleRepo = AppModule.provideSaleRepository()
        val licenseRepo = AppModule.provideLicenseRepository()
        
        // Crear ViewModels con dependencias
        val authViewModel: AuthViewModel = viewModel(
            factory = AuthViewModel.factory(authRepo)
        )
        
        val productsViewModel: ProductsViewModel = viewModel(
            factory = ProductsViewModel.factory(productRepo)
        )
        
        val salesViewModel: SalesViewModel = viewModel(
            factory = SalesViewModel.factory(productRepo, saleRepo)
        )
        
        val activationViewModel: ActivationViewModel = viewModel(
            factory = ActivationViewModel.factory(licenseRepo)
        )
        
        AppNavHost(
            authViewModel = authViewModel,
            productsViewModel = productsViewModel,
            salesViewModel = salesViewModel,
            activationViewModel = activationViewModel
        )
    }
}
