-- ============================================================================
-- FIX: get_inventario_dia
--
-- PROBLEMA ORIGINAL: cuando p_turno_ids es NULL (el caso normal, sin filtro
-- explícito desde la app), la función sumaba TODAS las ventas/productos/
-- mermas/devoluciones/eliminados del DÍA COMPLETO, sin importar a qué turno
-- pertenecían. Resultado: al cerrar un turno y abrir uno nuevo el mismo día,
-- la vista "de hoy" seguía mostrando (sumadas) las cantidades del turno ya
-- cerrado, porque nunca hubo un filtro de turno real en el camino por
-- defecto.
--
-- FIX: se calcula v_turno_id_actual (el turno más reciente del local para
-- esa fecha — el que está abierto, o el que se acaba de abrir tras un
-- cierre). Si el cliente no pide turnos específicos (p_turno_ids IS NULL):
--   - Si es HOY (no solo_lectura): se filtra automáticamente por
--     v_turno_id_actual. Así la vista "en vivo" siempre es del turno activo,
--     nunca arrastra datos de un turno ya cerrado.
--   - Si es un día PASADO (solo_lectura): se mantiene el comportamiento
--     anterior (todo el día), que es lo que necesita el calendario para
--     reportes históricos. El admin puede seguir acotando a turnos
--     puntuales de ese día pasado con el selector de turnos existente.
--
-- Cuando el cliente SÍ manda p_turno_ids explícito (selector de turnos del
-- calendario, o el turno nuevo recién creado tras cerrar_turno), se respeta
-- tal cual, igual que antes.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_inventario_dia(p_android_id text, p_local_id bigint, p_fecha date, p_turno_ids bigint[] DEFAULT NULL::bigint[])
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
    v_hoy date;
    v_turno_id_actual bigint;
    v_turno_ids_efectivo bigint[];
begin
    v_hoy := (now() AT TIME ZONE 'America/Havana')::date;

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

    v_solo_lectura := p_fecha < v_hoy;

    IF NOT v_solo_lectura AND v_usuario_id IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM turnos t WHERE t.local_id = p_local_id AND t.created_at::date = p_fecha) THEN
        BEGIN
            INSERT INTO turnos (local_id, cliente_id, usuario_id, apertura, created_at)
            VALUES (p_local_id, v_cliente_id, v_usuario_id, 0, p_fecha::timestamptz + time '00:00:00');
        EXCEPTION WHEN foreign_key_violation THEN
            NULL;
        END;
    END IF;

    -- Turno activo del día (el más reciente): si es hoy y no vino un turno
    -- explícito, este es el único turno que debe contar para todo lo de
    -- abajo. Se calcula DESPUÉS del posible insert de apertura de arriba,
    -- para que ya incluya el turno recién creado si fue el caso.
    select t.id into v_turno_id_actual
    from turnos t
    where t.local_id = p_local_id and t.created_at::date = p_fecha
    order by t.created_at desc limit 1;

    v_turno_ids_efectivo := coalesce(
        p_turno_ids,
        case when not v_solo_lectura then array[v_turno_id_actual] else null end
    );

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
        where v.local_id = p_local_id
          and v.created_at >= t.created_at
          and v.created_at < coalesce(
              (select min(t2.created_at) from turnos t2
               where t2.local_id = t.local_id and t2.created_at > t.created_at),
              'infinity'::timestamptz
          )
    ) tot on true
    where t.local_id = p_local_id and t.created_at::date = p_fecha
    order by t.created_at desc limit 1;


-- Productos nuevos
select coalesce(jsonb_agg(jsonb_build_object(
    'id', p.id, 'nombre', p.nombre, 'precio', p.precio,
    'stock', p.stock::int, 'ubicacion', p.ubicacion, 'fecha', p.created_at,
    'solicitado_por_nombre', us.nombre, 'resuelto_por_nombre', ur.nombre
) order by p.nombre), '[]'::jsonb) into v_productos_nuevos
from productos p
left join lateral (
    select a.solicitado_por, a.resuelto_por
    from aprobaciones a
    where a.producto_id = p.id and a.tipo = 'producto' and a.estado = 'aprobado'
    order by a.resuelto_at desc nulls last
    limit 1
) a on true
left join usuarios us on us.id = a.solicitado_por
left join usuarios ur on ur.id = a.resuelto_por
where p.local_id = p_local_id and p.created_at::date = p_fecha
  and (v_turno_ids_efectivo is null or exists (
    select 1 from turnos tt where tt.id = any(v_turno_ids_efectivo)
    and p.created_at >= tt.created_at
    and p.created_at < coalesce(
        (select min(t2.created_at) from turnos t2
         where t2.local_id = tt.local_id and t2.created_at > tt.created_at),
        'infinity'::timestamptz
    )
  ));

-- Productos modificados
select coalesce(jsonb_agg(jsonb_build_object(
    'id', p.id, 'nombre', p.nombre, 'precio', p.precio, 'stock', p.stock::int, 'fecha', p.updated_at
) order by p.nombre), '[]'::jsonb) into v_productos_modificados
from productos p
where p.local_id = p_local_id and p.updated_at::date = p_fecha and p.created_at::date <> p_fecha
  and (v_turno_ids_efectivo is null or exists (
    select 1 from turnos tt where tt.id = any(v_turno_ids_efectivo)
    and p.updated_at >= tt.created_at
    and p.updated_at < coalesce(
        (select min(t2.created_at) from turnos t2
         where t2.local_id = tt.local_id and t2.created_at > tt.created_at),
        'infinity'::timestamptz
    )
  ));

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
where d.local_id = p_local_id and d.created_at::date = p_fecha
  and (v_turno_ids_efectivo is null or exists (
    select 1 from turnos tt where tt.id = any(v_turno_ids_efectivo)
    and d.created_at >= tt.created_at
    and d.created_at < coalesce(
        (select min(t2.created_at) from turnos t2
         where t2.local_id = tt.local_id and t2.created_at > tt.created_at),
        'infinity'::timestamptz
    )
  ));

-- Mermas
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
  and (m.created_at::date = p_fecha or m.resuelto_at::date = p_fecha)
  and (v_turno_ids_efectivo is null or exists (
    select 1 from turnos tt where tt.id = any(v_turno_ids_efectivo)
    and coalesce(m.resuelto_at, m.created_at) >= tt.created_at
    and coalesce(m.resuelto_at, m.created_at) < coalesce(
        (select min(t2.created_at) from turnos t2
         where t2.local_id = tt.local_id and t2.created_at > tt.created_at),
        'infinity'::timestamptz
    )
  ));

-- Productos eliminados
select coalesce(jsonb_agg(jsonb_build_object(
    'id', pe.producto_id::text, 'nombre', pe.producto_nombre, 'stock', pe.stock::int,
    'fecha', pe.eliminado_en, 'resuelto_por_nombre', u.nombre
) order by pe.eliminado_en desc), '[]'::jsonb) into v_productos_eliminados
from productos_eliminados pe
left join usuarios u on u.id = pe.eliminado_por
where pe.local_id = p_local_id and pe.eliminado_en::date = p_fecha
  and (v_turno_ids_efectivo is null or exists (
    select 1 from turnos tt where tt.id = any(v_turno_ids_efectivo)
    and pe.eliminado_en >= tt.created_at
    and pe.eliminado_en < coalesce(
        (select min(t2.created_at) from turnos t2
         where t2.local_id = tt.local_id and t2.created_at > tt.created_at),
        'infinity'::timestamptz
    )
  ));
    -- Ventas del día
    select coalesce(jsonb_agg(jsonb_build_object(
        'id', v.id,
        'producto_nombre', coalesce(p.nombre, pe.producto_nombre, 'Producto eliminado'),
        'cantidad', v.cantidad::int, 'total', v.total,
        'metodo', v.metodo, 'efectivo', v.efectivo, 'transferencia', v.transferencia,
        'anulada', false, 'usuario_nombre', u.nombre, 'usuario_rol', u.rol, 'fecha', v.created_at,
        'cliente_ci', v.cliente_ci, 'cliente_tel', v.cliente_tel, 'cliente_nombre', v.cliente_nombre,
        'tarjeta_banco', tj.nombre, 'tarjeta_numero', tj.numero_cuenta, 'tarjeta_titular', null
    ) order by v.created_at desc), '[]'::jsonb) into v_ventas
    from ventas v
    left join productos p on p.id = v.producto_id::uuid
    left join productos_eliminados pe on pe.producto_id = v.producto_id::uuid
    left join usuarios u on u.id = v.usuario_id
    left join tarjetas tj on tj.id = v.tarjeta_id::uuid
    where v.local_id = p_local_id and v.created_at::date = p_fecha
      and (v_turno_ids_efectivo is null or exists (
        select 1 from turnos tt where tt.id = any(v_turno_ids_efectivo)
        and v.created_at >= tt.created_at
        and v.created_at < coalesce(
            (select min(t2.created_at) from turnos t2
             where t2.local_id = tt.local_id and t2.created_at > tt.created_at),
            'infinity'::timestamptz
        )
      ));

    -- Productos vendidos
    with ventas_base as (
        select v.producto_id, v.cantidad
        from ventas v
        where v.local_id = p_local_id and v.created_at::date = p_fecha
          and (v_turno_ids_efectivo is null or exists (
            select 1 from turnos tt where tt.id = any(v_turno_ids_efectivo)
            and v.created_at >= tt.created_at
            and v.created_at < coalesce(
                (select min(t2.created_at) from turnos t2
                 where t2.local_id = tt.local_id and t2.created_at > tt.created_at),
                'infinity'::timestamptz
            )
          ))
    ),
    productos_vendidos_agg as (
        select
            coalesce(p.id, pe.producto_id) as prod_id,
            coalesce(p.nombre, pe.producto_nombre, 'Producto eliminado') as prod_nombre,
            coalesce(p.stock, 0) as stock_actual,
            sum(vb.cantidad) as total_vendido
        from ventas_base vb
        left join productos p on p.id = vb.producto_id::uuid
        left join productos_eliminados pe on pe.producto_id = vb.producto_id::uuid
        group by coalesce(p.id, pe.producto_id), coalesce(p.nombre, pe.producto_nombre, 'Producto eliminado'), coalesce(p.stock, 0)
    )
    select coalesce(jsonb_agg(jsonb_build_object(
        'nombre', pva.prod_nombre,
        'total_vendido', pva.total_vendido::int,
        'total_actual', pva.stock_actual::int,
        'total_agregado', coalesce(ag.total, 0)::int,
        'total_merma', coalesce(me.total, 0)::int,
        'total_inicial', (pva.stock_actual + pva.total_vendido + coalesce(me.total, 0) - coalesce(ag.total, 0) - coalesce(de.total, 0))::int
    ) order by pva.prod_nombre), '[]'::jsonb) into v_productos_vendidos
    from productos_vendidos_agg pva
    left join lateral (
        select sum(a.cantidad) as total
        from aprobaciones a
        where a.producto_id = pva.prod_id and a.tipo = 'aumento' and a.estado = 'aprobado' and a.resuelto_at::date = p_fecha
    ) ag on true
    left join lateral (
        select sum(m.cantidad) as total
        from mermas m
        where m.producto_id = pva.prod_id and m.estado = 'aprobada' and m.resuelto_at::date = p_fecha
    ) me on true
    left join lateral (
        select sum(d.cantidad) as total
        from devoluciones d
        where d.producto_id = pva.prod_id and d.estado = 'aprobada_stock' and d.resuelto_at::date = p_fecha
    ) de on true;

    -- Totales de dinero
    select jsonb_build_object(
        'efectivo', coalesce(sum(v.efectivo), 0), 
        'transferencia', coalesce(sum(v.transferencia), 0),
        'tarjeta', coalesce(sum(v.transferencia) filter (where v.tarjeta_id is not null), 0),
        'total', coalesce(sum(v.total), 0),
        'cantidad_ventas', count(*)
    ) into v_totales
    from ventas v
    where v.local_id = p_local_id and v.created_at::date = p_fecha
      and (v_turno_ids_efectivo is null or exists (
        select 1 from turnos tt where tt.id = any(v_turno_ids_efectivo)
        and v.created_at >= tt.created_at
        and v.created_at < coalesce(
            (select min(t2.created_at) from turnos t2
             where t2.local_id = tt.local_id and t2.created_at > tt.created_at),
            'infinity'::timestamptz
        )
      ));

    -- Totales por tarjeta
    select coalesce(jsonb_agg(jsonb_build_object(
        'nombre', tx.nombre || ' · ' || tx.numero_cuenta,
        'total', tx.total
    ) order by tx.total desc), '[]'::jsonb) into v_totales_por_tarjeta
    from (
        select tj.nombre, tj.numero_cuenta, sum(v.transferencia) as total
        from ventas v
        join tarjetas tj on tj.id = v.tarjeta_id::uuid
        where v.local_id = p_local_id and v.created_at::date = p_fecha
          and v.tarjeta_id is not null
          and (v_turno_ids_efectivo is null or exists (
            select 1 from turnos tt where tt.id = any(v_turno_ids_efectivo)
            and v.created_at >= tt.created_at
            and v.created_at < coalesce(
                (select min(t2.created_at) from turnos t2
                 where t2.local_id = tt.local_id and t2.created_at > tt.created_at),
                'infinity'::timestamptz
            )
          ))
        group by tj.id, tj.nombre, tj.numero_cuenta
    ) tx;

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
