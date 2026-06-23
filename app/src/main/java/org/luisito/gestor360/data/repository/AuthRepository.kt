package org.luisito.gestor360.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import org.luisito.gestor360.data.SupabaseClientProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
            // 1. Iniciar sesión con Email/Contraseña
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            // 2. Esperar a que la sesión se inicialice (importante para persistencia)
            // Ver: https://github.com/supabase-community/supabase-kt/discussions/559 [citation:9]
            // y https://slack-chats.kotlinlang.org/t/23089132 [citation:6]
            supabase.auth.awaitInitialization()

            // 3. Obtener sesión después de inicialización
            val session = supabase.auth.currentSessionOrNull()
                ?: throw Exception("No se pudo obtener la sesión del usuario")
            val userId = session.user.id

            // 4. Consultar rol en tabla usuarios usando postgrest con decodeSingle<T>
            val userResult = supabase.postgrest["usuarios"]
                .select {
                    filter {
                        eq("auth_id", userId)
                    }
                }
                .decodeSingle<UsuarioRow>()

            LoginResult.Success(
                userId = userId,
                userRol = userResult.rol,
                username = userResult.username,
                nombre = userResult.nombre ?: userResult.username
            )

        }.getOrElse { exception ->
            val errorMessage = when {
                exception.message?.contains("Invalid login credentials") == true ->
                    "Credenciales incorrectas. Verifica tu usuario y contraseña."
                exception.message?.contains("Session not found") == true ||
                exception.message?.contains("No se pudo obtener la sesión") == true ->
                    "Error al iniciar sesión. Revisa tu conexión a internet."
                else -> exception.message ?: "Error de conexión"
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
