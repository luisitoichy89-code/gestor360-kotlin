package org.luisito.gestor360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import org.luisito.gestor360.data.repository.AuthRepository
import org.luisito.gestor360.data.repository.LicenciaRepository
import org.luisito.gestor360.data.repository.ProductRepository
import org.luisito.gestor360.data.repository.SaleRepository
import org.luisito.gestor360.ui.screens.*
import org.luisito.gestor360.ui.viewmodels.CartViewModel
import org.luisito.gestor360.ui.viewmodels.MainViewModel
import org.luisito.gestor360.ui.viewmodels.SalesViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    val authRepository = AuthRepository(applicationContext)
                    val licenciaRepository = LicenciaRepository(applicationContext)
                    val productRepository = ProductRepository(applicationContext)
                    val saleRepository = SaleRepository(applicationContext)

                    val mainViewModel: MainViewModel = viewModel(
                        factory = MainViewModelFactory(authRepository, licenciaRepository)
                    )

                    var pantalla by remember { mutableStateOf<Pantalla>(Pantalla.Inicio) }
                    var mensajeError by remember { mutableStateOf<String?>(null) }
                    val user by mainViewModel.currentUser.collectAsState()
                    val isLoading by mainViewModel.isLoading.collectAsState()
                    val error by mainViewModel.error.collectAsState()

                    val prefs = getSharedPreferences("gestor360_config", MODE_PRIVATE)
                    val clienteId = prefs.getString("cliente_id", null)

                    // Variables para la venta confirmada
                    var ventaConfirmada by remember { mutableStateOf<Pair<List<CartItem>, Triple<String, Double, Double>>?>(null) }

                    LaunchedEffect(error) {
                        if (error != null) {
                            mensajeError = error
                            mainViewModel.clearError()
                        }
                    }

                    when (pantalla) {
                        Pantalla.Inicio -> {
                            InicioScreen(
                                licenciaRepository = licenciaRepository,
                                onLicenciaValida = { pantalla = Pantalla.Login },
                                onLicenciaInvalida = { mensaje ->
                                    mensajeError = mensaje
                                    pantalla = Pantalla.LicenciaInvalida
                                }
                            )
                        }
                        Pantalla.LicenciaInvalida -> {
                            LicenciaInvalidaScreen(
                                mensaje = mensajeError ?: "Licencia no válida",
                                onReintentar = { pantalla = Pantalla.Inicio }
                            )
                        }
                        Pantalla.Login -> {
                            LoginScreen(
                                mainViewModel = mainViewModel,
                                onLoginSuccess = { pantalla = Pantalla.Dashboard }
                            )
                        }
                        Pantalla.Dashboard -> {
                            if (user != null) {
                                DashboardScreen(
                                    user = user!!,
                                    onVentasClick = { pantalla = Pantalla.Ventas },
                                    onLogout = {
                                        mainViewModel.logout()
                                        pantalla = Pantalla.Inicio
                                    }
                                )
                            }
                        }
                        Pantalla.Ventas -> {
                            val cartViewModel: CartViewModel = viewModel()
                            val salesViewModel: SalesViewModel = viewModel(
                                factory = SalesViewModelFactory(productRepository, saleRepository, "1")
                            )
                            var showCheckout by remember { mutableStateOf(false) }

                            if (!showCheckout) {
                                ProductSelectionScreen(
                                    user = user!!,
                                    cartViewModel = cartViewModel,
                                    salesViewModel = salesViewModel,
                                    onNavigateToCheckout = { showCheckout = true },
                                    onLogout = {
                                        mainViewModel.logout()
                                        pantalla = Pantalla.Inicio
                                    }
                                )
                            } else {
                                val cartItems by cartViewModel.cartItems.collectAsState()
                                val total by cartViewModel.total.collectAsState()

                                CheckoutScreen(
                                    cartItems = cartItems,
                                    total = total,
                                    onConfirmSale = { method, cash, transfer ->
                                        if (clienteId != null) {
                                            val success = salesViewModel.saveSale(
                                                cartItems = cartItems,
                                                total = total,
                                                method = method,
                                                cashAmount = cash,
                                                transferAmount = transfer,
                                                usuarioId = user!!.id,
                                                clienteId = clienteId
                                            )
                                            if (success) {
                                                ventaConfirmada = Triple(cartItems, Triple(method, cash, transfer))
                                                cartViewModel.clearCart()
                                                showCheckout = false
                                                pantalla = Pantalla.Confirmacion
                                            }
                                        } else {
                                            mensajeError = "Error: Cliente ID no encontrado"
                                        }
                                    },
                                    onCancel = { showCheckout = false }
                                )
                            }
                        }
                        Pantalla.Confirmacion -> {
                            ventaConfirmada?.let { (items, payment) ->
                                SaleConfirmationScreen(
                                    cartItems = items,
                                    total = items.sumOf { it.subtotal },
                                    method = payment.first,
                                    cashAmount = payment.second,
                                    transferAmount = payment.third,
                                    onNewSale = {
                                        ventaConfirmada = null
                                        pantalla = Pantalla.Ventas
                                    }
                                )
                            }
                        }
                    }

                    if (isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .wrapContentSize()
                        )
                    }
                }
            }
        }
    }
}

enum class Pantalla {
    Inicio, LicenciaInvalida, Login, Dashboard, Ventas, Confirmacion
}
