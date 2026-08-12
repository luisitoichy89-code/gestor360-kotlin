--
-- Fix: get_turnos_local devolvía SIEMPRE 0 filas cuando quien llamaba
-- no era admin, porque el EXISTS exigía au.rol = 'admin' sin excepción.
-- Por eso "Historial de turnos" no mostraba nada para el vendedor.
--
-- Ahora: el admin sigue viendo TODOS los turnos del local (igual que antes).
-- Un vendedor (o cualquier rol no-admin) ve solo SUS PROPIOS turnos.
-- Si el android_id no corresponde a un usuario activo de ese local,
-- se sigue devolviendo 0 filas (misma protección que antes).
--

CREATE OR REPLACE FUNCTION public.get_turnos_local(p_android_id text, p_local_id bigint) RETURNS TABLE(id bigint, cliente_id uuid, local_id bigint, usuario_id bigint, apertura numeric, cierre numeric, diferencia numeric, created_at timestamp with time zone, cierre_estado text, cierre_requested_at timestamp with time zone, cierre_processing_started_at timestamp with time zone, cierre_processing_finished_at timestamp with time zone, cierre_lock_version bigint, numero_turno integer, usuario_nombre text, usuario_rol text)
    LANGUAGE sql
    SET search_path TO 'public'
    AS $$
  with llamante as (
    select au.id, au.rol
    from public.usuarios au
    where au.android_id = p_android_id
      and au.local_id = p_local_id
      and au.activo = true
    limit 1
  )
  select
    t.id,
    t.cliente_id,
    t.local_id,
    t.usuario_id,
    t.apertura,
    t.cierre,
    t.diferencia,
    t.created_at,
    t.cierre_estado,
    t.cierre_requested_at,
    t.cierre_processing_started_at,
    t.cierre_processing_finished_at,
    t.cierre_lock_version,
    t.numero_turno,
    u.nombre as usuario_nombre,
    u.rol as usuario_rol
  from public.turnos t
  join public.usuarios u
    on u.id = t.usuario_id
  join llamante c
    on true
  where t.local_id = p_local_id
    and (c.rol = 'admin' or t.usuario_id = c.id)
  order by t.id desc;
$$;
