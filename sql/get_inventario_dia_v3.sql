-- ============================================================================
-- get_inventario_dia — v3
-- ============================================================================
-- v2 corrigió: colapso total (join uuid=text sin cast + agregado anidado en
-- totales_por_tarjeta), mermas ausente, y el bug de productos_nuevos (ese
-- se arregló en InventarioRepository.kt, no acá).
-- v3 agrega: productos_eliminados (requiere correr antes
-- productos_eliminados_setup.sql, que crea la tabla nueva + su trigger).
-- ============================================================================
-- 1) BUG (colapso total): join `p.id = v.producto_id` sin cast — productos.id
--    es uuid y ventas.producto_id es text. Postgres no tiene operador
--    "uuid = text", así que CUALQUIER llamada a esta función fallaba
--    (no solo cuando hay ventas con tarjeta). Fix: cast explícito
--    `v.producto_id::uuid` en los dos joins que lo usan.
--    OJO: ventas cuyo producto_id (text) sea NULL (legacy, solo tienen
--    producto_id_legacy bigint) van a desaparecer de "ventas" y de
--    "productos_vendidos" con este join — si eso te importa, avisa y lo
--    resolvemos con un join adicional contra producto_id_legacy.
--
-- 2) BUG (colapso total, más visible con varias tarjetas): en
--    totales_por_tarjeta, `jsonb_agg(jsonb_build_object(... sum(...)) order
--    by sum(...))` anida un agregado dentro de otro agregado sin subquery
--    — Postgres lo rechaza siempre ("aggregate function calls cannot be
--    nested"). Fix: se resuelve la suma por tarjeta en una subconsulta y
--    recién ahí se arma el jsonb_agg.
--
-- 3) BUG: la sección "mermas" nunca se calculaba ni se devolvía, aunque el
--    modelo Kotlin (MermaInfo) y la reconstrucción local ya la esperaban.
--    Fix: se agrega v_mermas y se incluye en el jsonb final. mermas.id es
--    bigint, se castea a texto para matchear MermaInfo.id (String) en Kotlin.
--
-- 4) BUG: "productos_nuevos" no es un bug de este SQL (la lógica ya estaba
--    bien: created_at::date = p_fecha, left join a aprobaciones), pero se
--    deja documentado acá porque el bug real vive en
--    InventarioRepository.kt (ver parche aparte).
--
-- PENDIENTE (no incluido, falta el archivo/tabla): "productos_eliminados"
-- tampoco se devuelve desde este SQL, mismo patrón que mermas. Si tienes la
-- tabla/RPC de bajas de producto, la agrego en la próxima pasada.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_inventario_dia(p_android_id text, p_local_id bigint, p_fecha date)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_catalog'
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
begin
    SELECT u.id, u.cliente_id, u.rol INTO v_usuario_id, v_cliente_id, v_rol
    FROM usuarios u
    WHERE u.android_id = p_android_id AND u.activo = true
    LIMIT 1;

    IF v_usuario_id IS NULL THEN
        SELECT l.cliente_id INTO v_cliente_id
        FROM licencias l
        WHERE l.device_id = p_android_id AND l.activo = true
        LIMIT 1;
        IF v_cliente_id IS NULL THEN
            RAISE EXCEPTION 'Dispositivo no autorizado';
        END IF;
    END IF;

    v_solo_lectura := p_fecha < current_date;

    IF NOT v_solo_lectura AND v_usuario_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM turnos t WHERE t.local_id = p_local_id AND t.created_at::date = p_fecha) THEN
        INSERT INTO turnos (local_id, cliente_id, usuario_id, apertura, created_at)
        VALUES (p_local_id, v_cliente_id, v_usuario_id, 0, p_fecha::timestamptz + time '00:00:00');
    END IF;

    -- Turno
    select jsonb_build_object(
        'id', t.id, 'apertura', t.apertura, 'cierre', t.cierre,
        'diferencia', case when t.cierre is not null then t.cierre - (t.apertura + coalesce(tot.esperado, 0)) end,
        'created_at', t.created_at, 'usuario_nombre', u.nombre, 'usuario_rol', u.rol
    ) into v_turno
    from turnos t
    left join usuarios u on u.id = t.usuario_id
    left join lateral (
        select sum(v.efectivo) esperado from ventas v
        where v.local_id = p_local_id and v.anulada = false and v.created_at::date = p_fecha
    ) tot on true
    where t.local_id = p_local_id and t.created_at::date = p_fecha
    order by t.created_at desc limit 1;

    -- Productos nuevos (lógica ya era correcta)
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', p.id, 'nombre', p.nombre, 'precio', p.precio,
        'stock', p.stock::int, 'ubicacion', p.ubicacion, 'fecha', p.created_at,
        'solicitado_por_nombre', us.nombre, 'resuelto_por_nombre', ur.nombre
    ) order by p.nombre), '[]'::jsonb) into v_productos_nuevos
    from productos p
    left join aprobaciones a on a.producto_id = p.id and a.tipo = 'producto' and a.estado = 'aprobado'
    left join usuarios us on us.id = a.solicitado_por
    left join usuarios ur on ur.id = a.resuelto_por
    where p.local_id = p_local_id and p.created_at::date = p_fecha;

    -- Productos modificados
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', p.id, 'nombre', p.nombre, 'precio', p.precio, 'stock', p.stock::int, 'fecha', p.updated_at
    ) order by p.nombre), '[]'::jsonb) into v_productos_modificados
    from productos p
    where p.local_id = p_local_id and p.updated_at::date = p_fecha and p.created_at::date <> p_fecha;

    -- Devueltos
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', d.id, 'producto_nombre', p.nombre, 'cantidad', d.cantidad::int, 'metodo', d.metodo,
        'estado', d.estado, 'solicitado_por_nombre', us.nombre,
        'resuelto_por_nombre', ur.nombre, 'resuelto_por_rol', ur.rol, 'fecha', d.created_at
    ) order by d.created_at), '[]'::jsonb) into v_devueltos
    from devoluciones d
    left join productos p on p.id = d.producto_id
    left join usuarios us on us.id = d.solicitado_por
    left join usuarios ur on ur.id = d.resuelto_por
    where d.local_id = p_local_id and d.created_at::date = p_fecha;

    -- NUEVO: Mermas del día (por creación o por resolución ese día)
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', m.id::text, 'producto_nombre', m.producto_nombre, 'cantidad', m.cantidad::int,
        'motivo', m.motivo, 'estado', m.estado,
        'solicitado_por_nombre', us.nombre, 'resuelto_por_nombre', ur.nombre,
        'fecha', coalesce(m.resuelto_at, m.created_at)
    ) order by m.created_at), '[]'::jsonb) into v_mermas
    from mermas m
    left join usuarios us on us.id = m.solicitado_por
    left join usuarios ur on ur.id = m.resuelto_por
    where m.local_id = p_local_id
      and (m.created_at::date = p_fecha or m.resuelto_at::date = p_fecha);

    -- NUEVO: Productos eliminados ese día (tabla productos_eliminados, ver
    -- productos_eliminados_setup.sql — se llena sola vía trigger AFTER DELETE)
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', pe.id, 'nombre', pe.nombre, 'stock', pe.stock::int,
        'fecha', pe.eliminado_at, 'resuelto_por_nombre', u.nombre
    ) order by pe.eliminado_at desc), '[]'::jsonb) into v_productos_eliminados
    from productos_eliminados pe
    left join usuarios u on u.id = pe.eliminado_por
    where pe.local_id = p_local_id and pe.eliminado_at::date = p_fecha;

    -- Ventas del día
    -- FIX: cast explícito v.producto_id::uuid (antes: uuid = text, rompía TODA la función)
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', v.id, 'producto_nombre', p.nombre, 'cantidad', v.cantidad::int, 'total', v.total,
        'metodo', v.metodo, 'efectivo', v.efectivo, 'transferencia', v.transferencia,
        'anulada', v.anulada, 'usuario_nombre', u.nombre, 'usuario_rol', u.rol, 'fecha', v.created_at,
        'cliente_ci', v.cliente_ci, 'cliente_tel', v.cliente_tel, 'cliente_nombre', v.cliente_nombre,
        'tarjeta_banco', tj.nombre, 'tarjeta_numero', tj.numero_cuenta, 'tarjeta_titular', null
    ) order by v.created_at desc), '[]'::jsonb) into v_ventas
    from ventas v
    join productos p on p.id = v.producto_id::uuid
    left join usuarios u on u.id = v.usuario_id
    left join tarjetas tj on tj.id = v.tarjeta_id::uuid
    where v.local_id = p_local_id and v.created_at::date = p_fecha;

    -- Productos vendidos ese día
    -- FIX: mismo cast v.producto_id::uuid
    select coalesce(jsonb_agg(jsonb_build_object(
        'nombre', x.nombre,
        'total_vendido', x.total_vendido::int,
        'total_actual', x.stock_actual::int,
        'total_agregado', x.total_agregado::int,
        'total_merma', x.total_merma::int,
        'total_inicial', (x.stock_actual + x.total_vendido + x.total_merma - x.total_agregado - x.total_devuelto)::int
    ) order by x.nombre), '[]'::jsonb) into v_productos_vendidos
    from (
        select p.id, p.nombre, p.stock as stock_actual,
            sum(v.cantidad) as total_vendido,
            coalesce((
                select sum(a.cantidad) from aprobaciones a
                where a.producto_id = p.id and a.tipo = 'aumento' and a.estado = 'aprobado' and a.resuelto_at::date = p_fecha
            ), 0) as total_agregado,
            coalesce((
                select sum(m.cantidad) from mermas m
                where m.producto_id = p.id and m.estado = 'aprobada' and m.resuelto_at::date = p_fecha
            ), 0) as total_merma,
            coalesce((
                select sum(d.cantidad) from devoluciones d
                where d.producto_id = p.id and d.estado = 'aprobada_stock' and d.resuelto_at::date = p_fecha
            ), 0) as total_devuelto
        from ventas v
        join productos p on p.id = v.producto_id::uuid
        where v.local_id = p_local_id and v.created_at::date = p_fecha and v.anulada = false
        group by p.id, p.nombre, p.stock
    ) x;

    -- Totales de dinero del día
    select jsonb_build_object(
        'efectivo', coalesce(sum(v.efectivo), 0),
        'transferencia', coalesce(sum(v.transferencia), 0),
        'tarjeta', coalesce(sum(v.transferencia) filter (where v.tarjeta_id is not null), 0),
        'total', coalesce(sum(v.total), 0),
        'cantidad_ventas', count(*)
    ) into v_totales
    from ventas v
    where v.local_id = p_local_id and v.created_at::date = p_fecha and v.anulada = false;

    -- Totales por tarjeta
    -- FIX: se resuelve el sum() por tarjeta en una subconsulta primero
    -- (antes: jsonb_agg(... order by sum(...)) anidaba dos agregados, error
    -- siempre, con cualquier cantidad de ventas con tarjeta).
    select coalesce(jsonb_agg(jsonb_build_object(
        'nombre', x.nombre || ' · ' || x.numero_cuenta,
        'total', x.total
    ) order by x.total desc), '[]'::jsonb) into v_totales_por_tarjeta
    from (
        select tj.nombre, tj.numero_cuenta, sum(v.transferencia) as total
        from ventas v
        join tarjetas tj on tj.id = v.tarjeta_id::uuid
        where v.local_id = p_local_id and v.created_at::date = p_fecha and v.anulada = false
          and v.tarjeta_id is not null
        group by tj.id, tj.nombre, tj.numero_cuenta
    ) x;

    return jsonb_build_object(
        'fecha', p_fecha,
        'solo_lectura', v_solo_lectura,
        'turno', v_turno,
        'productos_nuevos', v_productos_nuevos,
        'productos_modificados', v_productos_modificados,
        'devueltos', v_devueltos,
        'mermas', v_mermas,
        'productos_eliminados', v_productos_eliminados,
        'ventas', v_ventas,
        'productos_vendidos', v_productos_vendidos,
        'totales_ventas', v_totales,
        'totales_por_tarjeta', v_totales_por_tarjeta
    );
end;
$function$;
