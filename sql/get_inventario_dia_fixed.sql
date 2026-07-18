-- ============================================================================
-- get_inventario_dia — VERSIÓN CORREGIDA (auditoría)
-- ============================================================================
-- Cambios respecto a la versión original:
--
-- 1) BUG: el turno se creaba automáticamente para CUALQUIER p_fecha que se
--    consultara, incluidas fechas pasadas (modo solo lectura). Esto significa
--    que con solo navegar el calendario a un día viejo, el sistema insertaba
--    un turno "fantasma" con apertura=0 para ese día, que nunca existió.
--    Corrección: el auto-alta de turno ahora solo ocurre si p_fecha es HOY
--    (v_solo_lectura = false) y si hay un usuario identificado.
--
-- 2) BUG: si el dispositivo solo tenía licencia (sin usuario logueado),
--    v_usuario_id quedaba NULL y aun así se insertaba el turno con
--    usuario_id = NULL. Corrección: no se crea turno sin usuario conocido.
--
-- 3) BUG (dato mal calculado): "total_inicial" en productos_vendidos no
--    restaba/sumaba las devoluciones que vuelven a stock (estado
--    'aprobada_stock'), así que si un producto tuvo una devolución ese día,
--    el "total inicial" calculado quedaba descuadrado. Corrección: se suma
--    v_total_devuelto a la fórmula, igual que se hace con mermas y agregados.
--
-- 4) Se agrega 'totales_por_tarjeta' consistente con el modelo de la app
--    (ya se devolvía, pero no tenía representación en el modelo Kotlin;
--    ahora sí, ver InventarioDia.kt).
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

    -- Asegurar turno abierto para HOY si no existe.
    -- CORREGIDO: antes esto se ejecutaba para cualquier p_fecha (incluyendo
    -- fechas pasadas navegadas en modo solo lectura), creando turnos
    -- fantasma. Ahora solo aplica al día de hoy y con un usuario real.
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

    -- PRODUCTOS NUEVOS
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

    -- Ventas del día
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', v.id, 'producto_nombre', p.nombre, 'cantidad', v.cantidad::int, 'total', v.total,
        'metodo', v.metodo, 'efectivo', v.efectivo, 'transferencia', v.transferencia,
        'anulada', v.anulada, 'usuario_nombre', u.nombre, 'usuario_rol', u.rol, 'fecha', v.created_at,
        'cliente_ci', v.cliente_ci, 'cliente_tel', v.cliente_tel, 'cliente_nombre', v.cliente_nombre,
        'tarjeta_banco', tj.nombre, 'tarjeta_numero', tj.numero_cuenta, 'tarjeta_titular', null
    ) order by v.created_at desc), '[]'::jsonb) into v_ventas
    from ventas v
    join productos p on p.id = v.producto_id
    left join usuarios u on u.id = v.usuario_id
    left join tarjetas tj on tj.id = v.tarjeta_id::uuid
    where v.local_id = p_local_id and v.created_at::date = p_fecha;

    -- Productos vendidos ese día
    -- CORREGIDO: total_inicial ahora también contempla lo devuelto a stock
    -- ese día (devoluciones.estado = 'aprobada_stock'), que antes se ignoraba.
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
        join productos p on p.id = v.producto_id
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
    select coalesce(jsonb_agg(jsonb_build_object(
        'nombre', tj.nombre || ' · ' || tj.numero_cuenta,
        'total', sum(v.transferencia)
    ) order by sum(v.transferencia) desc), '[]'::jsonb) into v_totales_por_tarjeta
    from ventas v
    join tarjetas tj on tj.id = v.tarjeta_id::uuid
    where v.local_id = p_local_id and v.created_at::date = p_fecha and v.anulada = false
      and v.tarjeta_id is not null
    group by tj.id, tj.nombre, tj.numero_cuenta;

    return jsonb_build_object(
        'fecha', p_fecha,
        'solo_lectura', v_solo_lectura,
        'turno', v_turno,
        'productos_nuevos', v_productos_nuevos,
        'productos_modificados', v_productos_modificados,
        'devueltos', v_devueltos,
        'ventas', v_ventas,
        'productos_vendidos', v_productos_vendidos,
        'totales_ventas', v_totales,
        'totales_por_tarjeta', v_totales_por_tarjeta
    );
end;
$function$;
