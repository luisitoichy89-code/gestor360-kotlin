package org.luisito.gestor360.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.luisito.gestor360.data.SupabaseClientProvider

@Serializable
data class UsuarioRow(
    val id: String,
    val auth_id: String,
    val username: String,
    val rol: String,
    val nombre: String? = null
)

class AuthRepository {

    suspend fun login(username: String, password: String): LoginResult {
        val supabase = SupabaseClientProvider.client
        val email = "$username@gestor360.local"

        return runCatching {
            // 1. Iniciar sesión con timeout para conexiones lentas
            withTimeout(15000L) {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }

            // 2. Esperar a que la sesión se cargue desde almacenamiento local
            supabase.auth.awaitInitialization()

            // 3. Obtener sesión con manejo de null seguro
            val session = supabase.auth.currentSessionOrNull()
                ?: throw Exception("La sesión no se cargó correctamente. Verifica tu conexión a internet.")

            // 4. Obtener ID del usuario de forma segura
            val userId = session.user.id

            // 5. Consultar el rol en la tabla usuarios con filter { eq(...) }
            val userResponse = withTimeout(10000L) {
                supabase.postgrest.from("usuarios")
                    .select {
                        filter {
                            eq("auth_id", userId)
                        }
                    }
                    .decodeSingle<UsuarioRow>()
            }

            LoginResult.Success(
                userId = userId,
                userRol = userResponse.rol,
                username = userResponse.username,
                nombre = userResponse.nombre ?: userResponse.username
            )

        }.getOrElse { exception ->
            // Manejo profesional de errores con mensajes específicos
            val errorMessage = when {
                exception is java.util.concurrent.TimeoutException ||
                exception.message?.contains("timeout") == true ->
                    "La conexión está tardando más de lo esperado. Revisa tu conexión a internet."

                exception.message?.contains("Invalid login credentials") == true ->
                    "Credenciales incorrectas. Verifica tu usuario y contraseña."

                exception.message?.contains("Network") == true ||
                exception.message?.contains("connection") == true ->
                    "Error de conexión. Asegúrate de tener datos móviles o WiFi activos."

                exception.message?.contains("sesión no se cargó") == true ->
                    "No se pudo cargar tu sesión. Intenta nuevamente con mejor señal."

                else -> exception.message ?: "Error desconocido. Intenta reiniciar la aplicación."
            }

            LoginResult.Error(errorMessage)
        }
    }
}

sealed class LoginResult {
    data class Success(
        val userId: String,
        val userRol: String,
        val username: String,
        val nombre: String
    ) : LoginResult()

    data class Error(val message: String) : LoginResult()
}
