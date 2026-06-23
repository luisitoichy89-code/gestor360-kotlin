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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.luisito.gestor360.data.repository.AuthRepository
import org.luisito.gestor360.ui.screens.activation.ActivationScreen
import org.luisito.gestor360.ui.screens.dashboard.DashboardScreen
import org.luisito.gestor360.ui.screens.login.LoginScreen
import org.luisito.gestor360.ui.screens.login.LoginViewModel
import org.luisito.gestor360.ui.theme.Gestor360Theme
import org.luisito.gestor360.utils.DataStoreManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Gestor360Theme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val dataStoreManager = DataStoreManager(androidx.compose.ui.platform.LocalContext.current)
    val authRepository = AuthRepository()
    val loginViewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.Factory(authRepository, dataStoreManager)
    )

    NavHost(navController = navController, startDestination = "activation") {
        composable("activation") {
            ActivationScreen(navController)
        }
        composable("login") {
            LoginScreen(navController, loginViewModel)
        }
        composable("dashboard") {
            DashboardScreen(navController)
        }
    }
}
