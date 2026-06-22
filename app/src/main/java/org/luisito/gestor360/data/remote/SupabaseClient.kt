package org.luisito.gestor360.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SupabaseClient {
    private val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SupabaseConfig.URL,
        supabaseKey = SupabaseConfig.ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }

    fun getClient(): SupabaseClient = client

    suspend fun loginWithEmail(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = client.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            response.user != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getClienteIdFromDeviceId(deviceId: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.postgrest["licencias"]
                .select {
                    filter {
                        eq("device_id", deviceId)
                        eq("activo", true)
                    }
                }
                .decodeAs<List<Map<String, Any>>>()
            if (response.isNotEmpty()) {
                response.first()["cliente_id"] as? String
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
