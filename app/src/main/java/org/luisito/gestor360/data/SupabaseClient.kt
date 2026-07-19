package org.luisito.gestor360.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import org.luisito.gestor360.BuildConfig

object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            // Si un RPC agrega una clave nueva que el modelo Kotlin todavía no
            // tiene, con esto se ignora esa clave en vez de tumbar el decode
            // completo (esto fue justo lo que rompió el inventario: 'tarjeta'
            // y 'total' en totales_ventas no existían en TotalesVentas.kt).
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Postgrest)
            install(Auth) // ← OBLIGATORIO para que .auth exista
        }
    }
}
