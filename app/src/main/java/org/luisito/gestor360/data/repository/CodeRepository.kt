package org.luisito.gestor360.data.repository

import io.github.jan.supabase.postgrest.from
import org.luisito.gestor360.data.SupabaseClientProvider
import org.luisito.gestor360.data.models.CodeValidationResult

class CodeRepository {

    suspend fun validateCode(username: String, code: String): CodeValidationResult {
        return try {
            val supabase = SupabaseClientProvider.client
            val result = supabase.from("usuarios")
                .select {
                    filter {
                        eq("username", username)
                        eq("codigo_activacion", code)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()

            if (result.isNotEmpty()) {
                val user = result.first()
                CodeValidationResult(
                    isValid = true,
                    userId = user["id"] as? Int,
                    username = user["username"] as? String,
                    rol = user["rol"] as? String
                )
            } else {
                CodeValidationResult(isValid = false, message = "Código inválido o usuario incorrecto")
            }
        } catch (e: Exception) {
            CodeValidationResult(isValid = false, message = "Error al validar código: ${e.message}")
        }
    }

    suspend fun saveDeviceId(userId: Int, deviceId: String): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            supabase.from("usuarios")
                .update(
                    mapOf(
                        "device_id_pendiente" to deviceId,
                        "device_approved" to false
                    )
                ) {
                    filter {
                        eq("id", userId)
                    }
                }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkDeviceApproved(userId: Int): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            val result = supabase.from("usuarios")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()

            result.firstOrNull()?.get("device_approved") as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getDeviceIdPendiente(userId: Int): String? {
        return try {
            val supabase = SupabaseClientProvider.client
            val result = supabase.from("usuarios")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()

            result.firstOrNull()?.get("device_id_pendiente") as? String
        } catch (e: Exception) {
            null
        }
    }
}

    suspend fun createAuthUser(username: String, password: String): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            val email = "$username@gestor360.local"
            
            // Crear usuario en Supabase Auth usando API REST
            val result = supabase.auth.admin.createUser(
                email = email,
                password = password,
                emailConfirm = true
            )
            result.user != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loginWithPassword(username: String, password: String): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            val email = "$username@gestor360.local"
            val result = supabase.auth.signInWithPassword(
                email = email,
                password = password
            )
            result.user != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getUserId(username: String): Int? {
        return try {
            val supabase = SupabaseClientProvider.client
            val result = supabase.from("usuarios")
                .select {
                    filter {
                        eq("username", username)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()
            result.firstOrNull()?.get("id") as? Int
        } catch (e: Exception) {
            null
        }
    }

    suspend fun verifyDevice(userId: Int, deviceId: String): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            val result = supabase.from("usuarios")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()

            val user = result.firstOrNull()
            if (user == null) return false

            val deviceApproved = user["device_approved"] as? Boolean ?: false
            val deviceIdPendiente = user["device_id_pendiente"] as? String

            // Si el dispositivo está aprobado y el ID coincide
            if (deviceApproved && deviceIdPendiente == deviceId) {
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun requestPasswordRecovery(username: String): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            // Buscar el usuario para obtener su email
            val result = supabase.from("usuarios")
                .select {
                    filter {
                        eq("username", username)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()

            val user = result.firstOrNull()
            if (user == null) return false

            val email = "${username}@gestor360.local"
            // Enviar enlace de recuperación con Supabase Auth
            supabase.auth.resetPasswordForEmail(email)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkIfUserExists(username: String): Boolean {
        return try {
            val supabase = SupabaseClientProvider.client
            val result = supabase.from("usuarios")
                .select {
                    filter {
                        eq("username", username)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()
            result.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
