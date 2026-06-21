package org.luisito.gestor360.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import org.luisito.gestor360.BuildConfig

/**
 * Cliente único de Supabase para toda la app. Se conecta DIRECTO a Supabase
 * (sin Flask de por medio), usando la key publishable (sb_publishable_...)
 * — nunca la secret, ya que este código viaja dentro del APK del cliente.
 *
 * Las credenciales vienen de BuildConfig, que a su vez las recibe de
 * gradle.properties en tiempo de compilación (inyectadas desde GitHub
 * Secrets en el workflow de CI), nunca hardcodeadas aquí.
 */
object SupabaseClientProvider {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Postgrest)
            install(Auth)
        }
    }
}
