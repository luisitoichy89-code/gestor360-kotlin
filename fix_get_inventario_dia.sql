-- =====================================================================
-- PASO 1 — DIAGNÓSTICO (ejecutar primero, uno por uno, y revisar resultado)
-- =====================================================================

-- 1a) ¿La columna turno_id realmente existe en devoluciones, en el schema
--     y con el nombre exactos? (esto confirma o descarta que el ALTER se
--     haya aplicado en el proyecto/base correcta)
SELECT table_schema, table_name, column_name, data_type
FROM information_schema.columns
WHERE table_name = 'devoluciones' AND column_name = 'turno_id';

-- 1b) ¿Hay más de una versión de get_inventario_dia (overloads)? Esto pasa
--     cuando CREATE OR REPLACE se llamó con una firma de parámetros distinta
--     a la anterior: en vez de reemplazarla, Postgres crea OTRA función con
--     el mismo nombre. Si PostgREST/tu app terminan llamando a la vieja
--     (que puede seguir sin turno_id en otra rama, o tener otro bug ya
--     corregido en la nueva), verías errores que "no deberían" pasar.
SELECT p.oid::regprocedure AS firma, p.proname
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public' AND p.proname = 'get_inventario_dia';

-- Si el resultado de 1b) tiene MÁS DE UNA fila, ese es el problema. Copia el
-- valor exacto de "firma" de la fila que NO quieras y bórrala así:
--   DROP FUNCTION public.get_inventario_dia(text, bigint, date, bigint[]);
-- (ajusta los tipos según lo que te devuelva 1b)

-- 1c) Turnos duplicados/huérfanos de hoy para el local (esto es lo que
--     sospecho que está rompiendo también "Mis Ventas" con refresh, ver
--     nota al final del archivo)
SELECT id, local_id, usuario_id, apertura, cierre, created_at
FROM public.turnos
WHERE local_id = <PON_AQUI_TU_LOCAL_ID>
  AND created_at::date = current_date
ORDER BY created_at DESC;


-- =====================================================================
-- PASO 2 — FIX de la columna (idempotente, no falla si ya existe)
-- =====================================================================
ALTER TABLE public.devoluciones
  ADD COLUMN IF NOT EXISTS turno_id bigint;

-- Fuerza a PostgREST a refrescar su caché de metadata (por si acaso)
NOTIFY pgrst, 'reload schema';


-- =====================================================================
-- PASO 3 — Recrear la función limpia (elimina cualquier overload viejo
-- con esta firma exacta y la vuelve a crear)
-- =====================================================================
DROP FUNCTION IF EXISTS public.get_inventario_dia(text, bigint, date, bigint[]);

CREATE OR REPLACE FUNCTION public.get_inventario_dia(p_android_id text, p_local_id bigint, p_fecha date, p_turno_ids bigint[] DEFAULT NULL::bigint[])
 RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public', 'pg_catalog'
AS $function$
declare
    v_usuario_id bigint;
    v_cliente_id uuid;
    v_rol text;
    v_turno jsonb;
    v_productos_nuevos jsonb;
    v_productos_modificados jsonb;
    v_devueltos jsonb;
    v_mermas jsonb;
    v_productos_eliminados jsonb;
    v_ventas jsonb;
    v_productos_vendidos jsonb;
    v_totales jsonb;
    v_totales_por_tarjeta jsonb;
    v_solo_lectura boolean;
    v_hoy date;
    v_turno_id_actual bigint;
    v_turno_ids_efectivo bigint[];
begin
    v_hoy := (now() AT TIME ZONE 'America/Havana')::date;
    SELECT u.id, u.cliente_id, u.rol INTO v_usuario_id, v_cliente_id, v_rol FROM usuarios u WHERE u.android_id = p_android_id AND u.activo = true LIMIT 1;
    IF v_usuario_id IS NULL THEN SELECT l.cliente_id INTO v_cliente_id FROM licencias l WHERE l.device_id = p_android_id AND l.activo = true LIMIT 1; IF v_cliente_id IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF; END IF;
    v_solo_lectura := p_fecha < v_hoy;

    -- FIX: el turno "actual" ya NO es simplemente el de created_at más
    -- reciente del día (eso podía agarrar un turno duplicado/huérfano y
    -- dejar fuera las ventas reales, causando totales en cero). Ahora es
    -- el que sigue ABIERTO (cierre IS NULL); si por algún motivo hay más
    -- de uno abierto, se usa el más antiguo de los abiertos, que es el que
    -- realmente arrancó la jornada.
    select t.id into v_turno_id_actual
    from turnos t
    where t.local_id = p_local_id and t.created_at::date = p_fecha and t.cierre is null
    order by t.created_at asc limit 1;

    v_turno_ids_efectivo := coalesce(p_turno_ids, case when not v_solo_lectura then array[v_turno_id_actual] else null end);

    select jsonb_build_object('id', t.id, 'apertura', t.apertura, 'cierre', t.cierre, 'diferencia', case when t.cierre is not null then t.cierre - (t.apertura + coalesce(tot.esperado, 0)) end, 'created_at', t.created_at, 'usuario_nombre', u.nombre, 'usuario_rol', u.rol) into v_turno
    from turnos t left join usuarios u on u.id = t.usuario_id left join lateral (select sum(v.efectivo) esperado from ventas v where v.local_id = p_local_id and v.created_at >= t.created_at and v.created_at < coalesce((select min(t2.created_at) from turnos t2 where t2.local_id = t.local_id and t2.created_at > t.created_at), 'infinity'::timestamptz)) tot on true
    where t.id = coalesce(v_turno_id_actual, (select id from turnos where local_id = p_local_id and created_at::date = p_fecha order by created_at desc limit 1));

    -- Productos nuevos
    select coalesce(jsonb_agg(jsonb_build_object('id', p.id, 'nombre', p.nombre, 'precio', p.precio, 'stock', p.stock::int, 'ubicacion', p.ubicacion, 'fecha', p.created_at, 'solicitado_por_nombre', us.nombre, 'resuelto_por_nombre', ur.nombre) order by p.nombre), '[]'::jsonb) into v_productos_nuevos
    from productos p left join lateral (select a.solicitado_por, a.resuelto_por from aprobaciones a where a.producto_id = p.id and a.tipo = 'producto' and a.estado = 'aprobado' order by a.resuelto_at desc nulls last limit 1) a on true left join usuarios us on us.id = a.solicitado_por left join usuarios ur on ur.id = a.resuelto_por
    where p.local_id = p_local_id and p.created_at::date = p_fecha;

    -- Productos modificados
    select coalesce(jsonb_agg(jsonb_build_object('id', p.id, 'nombre', p.nombre, 'precio', p.precio, 'stock', p.stock::int, 'fecha', p.updated_at) order by p.nombre), '[]'::jsonb) into v_productos_modificados
    from productos p where p.local_id = p_local_id and p.updated_at::date = p_fecha and p.created_at::date <> p_fecha;

    -- Devueltos
    select coalesce(jsonb_agg(jsonb_build_object('id', d.id, 'producto_nombre', p.nombre, 'cantidad', d.cantidad::int, 'metodo', d.metodo, 'estado', d.estado, 'solicitado_por_nombre', us.nombre, 'resuelto_por_nombre', ur.nombre, 'resuelto_por_rol', ur.rol, 'fecha', d.created_at) order by d.created_at), '[]'::jsonb) into v_devueltos
    from devoluciones d left join productos p on p.id = d.producto_id left join usuarios us on us.id = d.solicitado_por left join usuarios ur on ur.id = d.resuelto_por
    where d.local_id = p_local_id and d.created_at::date = p_fecha and (v_turno_ids_efectivo is null or d.turno_id = any(v_turno_ids_efectivo));

    -- Mermas
    select coalesce(jsonb_agg(jsonb_build_object('id', m.id::text, 'producto_nombre', m.producto_nombre, 'cantidad', m.cantidad::int, 'motivo', m.motivo, 'estado', m.estado, 'solicitado_por_nombre', us.nombre, 'resuelto_por_nombre', ur.nombre, 'fecha', coalesce(m.resuelto_at, m.created_at)) order by m.created_at), '[]'::jsonb) into v_mermas
    from mermas m left join usuarios us on us.id = m.solicitado_por left join usuarios ur on ur.id = m.resuelto_por
    where m.local_id = p_local_id and (m.created_at::date = p_fecha or m.resuelto_at::date = p_fecha) and (v_turno_ids_efectivo is null or m.turno_id = any(v_turno_ids_efectivo));

    -- Productos eliminados
    select coalesce(jsonb_agg(jsonb_build_object('id', pe.producto_id::text, 'nombre', pe.producto_nombre, 'stock', pe.stock::int, 'fecha', pe.eliminado_en, 'resuelto_por_nombre', u.nombre) order by pe.eliminado_en desc), '[]'::jsonb) into v_productos_eliminados
    from productos_eliminados pe left join usuarios u on u.id = pe.eliminado_por
    where pe.local_id = p_local_id and pe.eliminado_en::date = p_fecha and (v_turno_ids_efectivo is null or pe.turno_id = any(v_turno_ids_efectivo));

    -- Ventas del día
    select coalesce(jsonb_agg(jsonb_build_object('id', v.id, 'producto_nombre', coalesce(p.nombre, pe.producto_nombre, 'Producto eliminado'), 'cantidad', v.cantidad::int, 'total', v.total, 'metodo', v.metodo, 'efectivo', v.efectivo, 'transferencia', v.transferencia, 'anulada', false, 'usuario_nombre', u.nombre, 'usuario_rol', u.rol, 'fecha', v.created_at, 'cliente_ci', v.cliente_ci, 'cliente_tel', v.cliente_tel, 'cliente_nombre', v.cliente_nombre, 'tarjeta_banco', tj.nombre, 'tarjeta_numero', tj.numero_cuenta, 'tarjeta_titular', null) order by v.created_at desc), '[]'::jsonb) into v_ventas
    from ventas v left join productos p on p.id = v.producto_id::uuid left join productos_eliminados pe on pe.producto_id = v.producto_id::uuid left join usuarios u on u.id = v.usuario_id left join tarjetas tj on tj.id = v.tarjeta_id::uuid
    where v.local_id = p_local_id and v.created_at::date = p_fecha and (v_turno_ids_efectivo is null or v.turno_id = any(v_turno_ids_efectivo));

    -- Totales de dinero
    select jsonb_build_object('efectivo', coalesce(sum(v.efectivo), 0), 'transferencia', coalesce(sum(v.transferencia), 0), 'tarjeta', coalesce(sum(v.transferencia) filter (where v.tarjeta_id is not null), 0), 'total', coalesce(sum(v.total), 0), 'cantidad_ventas', count(*)) into v_totales
    from ventas v where v.local_id = p_local_id and v.created_at::date = p_fecha and (v_turno_ids_efectivo is null or v.turno_id = any(v_turno_ids_efectivo));

    -- Totales por tarjeta
    select coalesce(jsonb_agg(jsonb_build_object('nombre', tx.nombre || ' · ' || tx.numero_cuenta, 'total', tx.total) order by tx.total desc), '[]'::jsonb) into v_totales_por_tarjeta
    from (select tj.nombre, tj.numero_cuenta, sum(v.transferencia) as total from ventas v join tarjetas tj on tj.id = v.tarjeta_id::uuid where v.local_id = p_local_id and v.created_at::date = p_fecha and v.tarjeta_id is not null and (v_turno_ids_efectivo is null or v.turno_id = any(v_turno_ids_efectivo)) group by tj.id, tj.nombre, tj.numero_cuenta) tx;

    return jsonb_build_object('fecha', p_fecha, 'solo_lectura', v_solo_lectura, 'turno', v_turno, 'productos_nuevos', v_productos_nuevos, 'productos_modificados', v_productos_modificados, 'devueltos', v_devueltos, 'mermas', v_mermas, 'productos_eliminados', v_productos_eliminados, 'ventas', v_ventas, 'productos_vendidos', v_productos_vendidos, 'totales_ventas', v_totales, 'totales_por_tarjeta', v_totales_por_tarjeta);
end;
$function$;

-- =====================================================================
-- NOTA sobre el Problema 2 (Mis Ventas en cero al refrescar)
-- =====================================================================
-- MisVentasScreen.kt filtra las ventas en el teléfono comparando contra
-- turno_cache (tabla local Room), que se llena en segundo plano cuando
-- OTRA pantalla llama a get_inventario_dia y guarda el campo "turno" que
-- devuelve el servidor. Si en la tabla turnos quedaron filas duplicadas
-- de hoy (por ejemplo del INSERT INTO turnos que ya quitaste del RPC),
-- el turno que el server reporta como "el de hoy" puede tener un
-- created_at POSTERIOR a ventas que ya se hicieron bajo el turno real
-- -> al refrescar, el filtro local descarta esas ventas y los totales
-- caen a 0. El cambio de arriba (elegir el turno por cierre IS NULL, el
-- más antiguo abierto, no el created_at más reciente) corrige esto del
-- lado del servidor. Para terminar de blindarlo del lado de la app,
-- necesito ver TurnoDao.kt, VentaDao.kt y TurnoEntity.kt (no venían en
-- el zip) para confirmar cómo se define "turno activo" en Room y, si
-- hace falta, aplicar el mismo criterio ahí.
