package org.luisito.gestor360.utils

import android.content.Context

/**
 * Toggle: "Confirmación de venta por SMS" activa/inactiva.
 *
 * Por defecto queda en false — o sea, si nadie lo toca, el checkout se
 * comporta exactamente como antes (del carrito pasa directo a datos del
 * cliente, sin pasar por el overlay de espera de SMS).
 *
 * OJO — esto se guarda en SharedPreferences, SOLO en este dispositivo. No
 * tengo acceso a tu base de datos de Supabase para crear la tabla/RPC que
 * lo sincronizaría entre los teléfonos de varios cajeros, así que si el
 * negocio usa más de un dispositivo, cada uno hay que activarlo/desactivarlo
 * por separado. Al final de este archivo dejo el SQL sugerido por si más
 * adelante quieres que lo sincronice de verdad vía Supabase (avísame y lo
 * conecto en cuanto exista esa tabla/RPC del lado del servidor).
 */
object ConfigManager {
    private const val PREFS = "gestor360_config"
    private const val KEY_SMS = "confirmacion_sms_activa"

    fun confirmacionSmsActiva(context: Context, clienteId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("${KEY_SMS}_$clienteId", false)

    fun setConfirmacionSmsActiva(context: Context, clienteId: String, activa: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("${KEY_SMS}_$clienteId", activa)
            .apply()
    }
}

/*
SQL sugerido para cuando quieras sincronizarlo entre dispositivos vía Supabase
(pendiente — no lo creé porque no tengo acceso a tu base de datos):

create table if not exists configuracion_negocio (
    cliente_id text primary key references clientes(id),
    confirmacion_sms_activa boolean not null default false
);

-- RPC de lectura (mismo patrón que el resto del proyecto: resuelve cliente_id
-- a partir del android_id, así el cliente nunca manda cliente_id directamente)
create or replace function obtener_configuracion_negocio(p_android_id text)
returns table(confirmacion_sms_activa boolean)
language sql security definer as $$
    select coalesce(cn.confirmacion_sms_activa, false)
    from users u
    left join configuracion_negocio cn on cn.cliente_id = u.cliente_id
    where u.android_id = p_android_id
    limit 1;
$$;

-- RPC de escritura (agregar validación de que el usuario sea admin dentro
-- de la función, no confiar solo en el chequeo del cliente)
create or replace function actualizar_confirmacion_sms(p_android_id text, p_activa boolean)
returns void
language sql security definer as $$
    insert into configuracion_negocio (cliente_id, confirmacion_sms_activa)
    select u.cliente_id, p_activa from users u
    where u.android_id = p_android_id and u.rol = 'admin'
    on conflict (cliente_id) do update set confirmacion_sms_activa = p_activa;
$$;
*/
