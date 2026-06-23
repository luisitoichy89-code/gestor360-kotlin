package org.luisito.gestor360.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import org.luisito.gestor360.data.SupabaseClientProvider

class AuthRepository {

    suspend fun login(username: String, password: String): LoginResult {
        val supabase = SupabaseClientProvider.client
        val email = "$username@gestor360.local"

        return runCatching {
            // 1. Iniciar sesión
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            // 2. Esperar a que la sesión se inicialice
            supabase.auth.awaitInitialization()

            // 3. Obtener sesión
            val session = supabase.auth.currentSessionOrNull()
                ?: throw Exception("No se pudo obtener la sesión")

            val userId = session.user.id

            // 4. Consultar rol en tabla usuarios
            val userResult = supabase.postgrest["usuarios"]
                .select {
                    filter {
                        eq("auth_id", userId)
                    }
                }
                .decodeSingle<Map<String, Any>>()

            val userRol = userResult["rol"] as? String ?: throw Exception("Usuario sin rol")

            LoginResult.Success(userId, userRol)

        }.getOrElse { exception ->
            val errorMessage = if (exception.message?.contains("Invalid login credentials") == true) {
                "Credenciales incorrectas"
            } else {
                exception.message ?: "Error de conexión"
            }
            LoginResult.Error(errorMessage)
        }
    }
}

sealed class LoginResult {
    data class Success(val userId: String, val userRol: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}
