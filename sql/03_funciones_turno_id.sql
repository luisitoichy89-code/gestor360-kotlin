-- ============================================================================
-- PASO 3 de 3 — FUNCIONES (correr después del PASO 1 y 2)
--
-- Cada función que crea una venta/merma/devolución/turno ahora:
--   1) toma un candado por local (pg_advisory_xact_lock) antes de decidir
--      "¿hay turno abierto o hay que crear uno", para que dos peticiones
--      simultáneas (dos ventas a la vez sin turno abierto, o una venta justo
--      cuando el admin está cerrando turno) no puedan pisarse y dejar una
--      venta mal asignada o dos turnos abiertos a la vez. El candado se
--      libera solo al terminar la transacción (xact = "transaction-scoped").
--   2) estampa turno_id directo en el INSERT, ya no se infiere después.
--
-- registrar_venta además ahora SÍ guarda usuario_id (antes quedaba siempre
-- NULL — era la causa de que "Mis Ventas" no pudiera separar por vendedor).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- registrar_venta
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.registrar_venta(
    p_android_id text, p_local_id bigint, p_id text, p_producto_id text,
    p_cantidad numeric, p_total numeric, p_metodo text, p_efectivo numeric, p_transferencia numeric,
    p_cliente_ci text DEFAULT ''::text, p_cliente_tel text DEFAULT ''::text, p_cliente_nombre text DEFAULT ''::text,
    p_tarjeta_id text DEFAULT NULL::text, p_accion_id uuid DEFAULT NULL::uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $function$
DECLARE
    v_usuario_id bigint;
    v_cliente_id uuid;
    v_turno_id bigint;
BEGIN
    v_usuario_id := public.validar_usuario_venta(p_android_id, p_local_id);

    IF p_accion_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id
    ) THEN
        RETURN;
    END IF;

    SELECT cliente_id INTO v_cliente_id FROM locales WHERE id = p_local_id;

    -- Candado por local: serializa "¿hay turno abierto?" para que dos ventas
    -- simultáneas sin turno abierto no terminen creando dos turnos abiertos
    -- al mismo tiempo.
    PERFORM pg_advisory_xact_lock(hashtext('turno_local_' || p_local_id::text)::bigint);

    -- Se usa la función ya existente en el proyecto (obtener_turno_abierto)
    -- en vez de repetir esta consulta acá — una sola fuente de verdad de
    -- "cuál es el turno abierto de este local".
    v_turno_id := public.obtener_turno_abierto(p_local_id);

    IF v_turno_id IS NULL THEN
        INSERT INTO turnos (local_id, cliente_id, usuario_id, apertura, created_at)
        VALUES (p_local_id, v_cliente_id, v_usuario_id, 0, now())
        RETURNING id INTO v_turno_id;
    END IF;

    INSERT INTO public.ventas (
        id, local_id, producto_id, cantidad, total, metodo, efectivo, transferencia,
        cliente_ci, cliente_tel, cliente_nombre, tarjeta_id, created_at, turno_id, usuario_id
    )
    VALUES (
        p_id, p_local_id, p_producto_id, p_cantidad, p_total, p_metodo, p_efectivo, p_transferencia,
        NULLIF(p_cliente_ci, ''), NULLIF(p_cliente_tel, ''), NULLIF(p_cliente_nombre, ''), p_tarjeta_id,
        now(), v_turno_id, v_usuario_id
    )
    ON CONFLICT (id) DO NOTHING;

    IF p_producto_id IS NOT NULL THEN
        UPDATE public.productos SET stock = stock - p_cantidad WHERE id = p_producto_id::uuid;
    END IF;

    IF p_accion_id IS NOT NULL THEN
        INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'registrar_venta') ON CONFLICT (accion_id) DO NOTHING;
    END IF;
END;
$function$;

-- ---------------------------------------------------------------------------
-- crear_merma
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.crear_merma(p_android_id text, p_local_id bigint, p_id uuid, p_producto_id uuid, p_cantidad numeric, p_motivo text, p_accion_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_user_id bigint;
    v_user_nombre text;
    v_producto_nombre text;
    v_turno_id bigint;
BEGIN
    SELECT id, nombre INTO v_user_id, v_user_nombre FROM public.usuarios WHERE android_id = p_android_id AND activo = true LIMIT 1;
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Usuario no autorizado';
    END IF;
    IF EXISTS (SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id) THEN RETURN; END IF;
    SELECT nombre INTO v_producto_nombre FROM public.productos WHERE id = p_producto_id;

    -- Turno abierto actual, si hay (crear_merma no abre turno nuevo: pedir
    -- una merma no debería, por sí sola, arrancar un turno de caja).
    v_turno_id := public.obtener_turno_abierto(p_local_id);

    INSERT INTO public.mermas (id, local_id, producto_id, producto_nombre, cantidad, motivo, solicitado_por, solicitado_por_nombre, estado, turno_id)
    VALUES (p_id, p_local_id, p_producto_id, COALESCE(v_producto_nombre, ''), p_cantidad, p_motivo, v_user_id, v_user_nombre, 'pendiente', v_turno_id)
    ON CONFLICT (id) DO NOTHING;
    INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'crear_merma');
END;
$function$;

-- ---------------------------------------------------------------------------
-- resolver_merma
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.resolver_merma(p_android_id text, p_local_id bigint, p_id uuid, p_estado text, p_accion_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_admin_id bigint;
    v_admin_nombre text;
    v_producto_id uuid;
    v_cantidad numeric;
    v_estado_actual text;
    v_turno_id_resuelto bigint;
BEGIN
    SELECT id, nombre INTO v_admin_id, v_admin_nombre FROM public.usuarios WHERE android_id = p_android_id AND activo = true AND rol = 'admin' LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'No autorizado';
    END IF;
    IF p_estado NOT IN ('aprobada', 'rechazada') THEN
        RAISE EXCEPTION 'p_estado inválido: %, debe ser aprobada o rechazada', p_estado;
    END IF;
    IF EXISTS (SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id) THEN RETURN; END IF;
    SELECT producto_id, cantidad, estado INTO v_producto_id, v_cantidad, v_estado_actual
    FROM public.mermas WHERE id = p_id AND local_id = p_local_id;
    IF v_estado_actual IS NULL THEN
        RAISE EXCEPTION 'Merma % no encontrada en el local %', p_id, p_local_id;
    END IF;
    IF v_estado_actual <> 'pendiente' THEN
        INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'resolver_merma');
        RETURN;
    END IF;

    v_turno_id_resuelto := public.obtener_turno_abierto(p_local_id);

    UPDATE public.mermas
    SET estado = p_estado, resuelto_por = v_admin_id, resuelto_por_nombre = v_admin_nombre, resuelto_at = now(), turno_id_resuelto = v_turno_id_resuelto
    WHERE id = p_id AND local_id = p_local_id;
    IF p_estado = 'aprobada' THEN
        UPDATE public.productos SET stock = GREATEST(stock - v_cantidad, 0)
        WHERE id = v_producto_id AND local_id = p_local_id;
    END IF;
    INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'resolver_merma');
END;
$function$;

-- ---------------------------------------------------------------------------
-- solicitar_devolucion
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.solicitar_devolucion(p_android_id text, p_local_id bigint, p_producto_id uuid, p_cantidad numeric, p_metodo text, p_motivo text, p_id uuid, p_accion_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_catalog'
AS $function$
declare
    v_user_id bigint;
    v_cliente_id uuid;
    v_usuario_nombre text;
    v_producto_nombre text;
    v_turno_id bigint;
begin
    v_user_id := public.validar_usuario_local(p_android_id, p_local_id);

    IF EXISTS (
        SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id
    ) THEN
        RETURN;
    END IF;

    SELECT u.cliente_id, u.nombre
    INTO v_cliente_id, v_usuario_nombre
    FROM public.usuarios u
    WHERE u.android_id = p_android_id
      AND u.activo = true
    LIMIT 1;

    IF v_cliente_id IS NULL THEN
        SELECT l.cliente_id
        INTO v_cliente_id
        FROM public.licencias l
        WHERE l.device_id = p_android_id
          AND l.activo = true
        LIMIT 1;

        IF v_cliente_id IS NULL THEN
            RAISE EXCEPTION 'Dispositivo no autorizado';
        END IF;

        v_usuario_nombre := '';
    END IF;

    SELECT nombre INTO v_producto_nombre
    FROM public.productos
    WHERE id = p_producto_id
      AND local_id = p_local_id;

    IF v_producto_nombre IS NULL THEN
        RAISE EXCEPTION 'Producto % no existe en este local', p_producto_id;
    END IF;

    v_turno_id := public.obtener_turno_abierto(p_local_id);

    INSERT INTO public.devoluciones (
        id, cliente_id, local_id, producto_id, producto_nombre, cantidad, metodo, motivo,
        solicitado_por, solicitado_por_nombre, estado, turno_id
    )
    VALUES (
        p_id, v_cliente_id, p_local_id, p_producto_id, v_producto_nombre, p_cantidad, p_metodo, p_motivo,
        v_user_id, v_usuario_nombre, 'pendiente', v_turno_id
    );

    INSERT INTO public.acciones_procesadas (accion_id, tipo)
    VALUES (p_accion_id, 'solicitar_devolucion');
END;
$function$;

-- ---------------------------------------------------------------------------
-- resolver_devolucion (versión con p_destino — la que usa la app hoy)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.resolver_devolucion(p_android_id text, p_local_id bigint, p_id uuid, p_estado text, p_accion_id uuid, p_destino text DEFAULT NULL::text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_admin_id bigint;
    v_admin_nombre text;
    v_producto_id uuid;
    v_cantidad numeric;
    v_estado_actual text;
    v_estado_final text;
    v_turno_id_resuelto bigint;
BEGIN
    SELECT id, nombre INTO v_admin_id, v_admin_nombre
    FROM public.usuarios WHERE android_id = p_android_id AND activo = true AND rol = 'admin' LIMIT 1;
    IF v_admin_id IS NULL THEN
        RAISE EXCEPTION 'No autorizado';
    END IF;

    IF p_estado NOT IN ('aprobada', 'rechazada') THEN
        RAISE EXCEPTION 'p_estado inválido: %, debe ser aprobada o rechazada', p_estado;
    END IF;

    IF p_estado = 'aprobada' AND p_destino NOT IN ('stock', 'merma') THEN
        RAISE EXCEPTION 'p_destino inválido: %, debe ser stock o merma', p_destino;
    END IF;

    IF EXISTS (SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id) THEN RETURN; END IF;

    SELECT producto_id, cantidad, estado
    INTO v_producto_id, v_cantidad, v_estado_actual
    FROM public.devoluciones
    WHERE id = p_id AND local_id = p_local_id;

    IF v_estado_actual IS NULL THEN
        RAISE EXCEPTION 'Devolución % no encontrada en el local %', p_id, p_local_id;
    END IF;

    IF v_estado_actual <> 'pendiente' THEN
        INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'resolver_devolucion');
        RETURN;
    END IF;

    v_estado_final := case
        when p_estado = 'aprobada' and p_destino = 'stock' then 'aprobada_stock'
        when p_estado = 'aprobada' and p_destino = 'merma' then 'aprobada_merma'
        else 'rechazada'
    end;

    v_turno_id_resuelto := public.obtener_turno_abierto(p_local_id);

    UPDATE public.devoluciones
    SET estado = v_estado_final, resuelto_por = v_admin_id, resuelto_por_nombre = v_admin_nombre, resuelto_at = now(), turno_id_resuelto = v_turno_id_resuelto
    WHERE id = p_id AND local_id = p_local_id;

    IF v_estado_final = 'aprobada_stock' THEN
        UPDATE public.productos SET stock = GREATEST(stock + v_cantidad, 0)
        WHERE id = v_producto_id AND local_id = p_local_id;
    END IF;

    INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'resolver_devolucion');
END;
$function$;

-- ---------------------------------------------------------------------------
-- eliminar_producto
--
-- ACTUALIZACIÓN: el trigger real (fn_registrar_producto_eliminado) ya
-- resuelve el turno por su cuenta llamando a public.obtener_turno_abierto(),
-- no hace falta pasarle nada desde acá. La única señal que sí necesita y
-- que ya existía de antes es app.usuario_id_actual (para eliminado_por).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.eliminar_producto(p_android_id text, p_local_id bigint, p_id uuid, p_accion_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_usuario_id bigint;
BEGIN
    SELECT id INTO v_usuario_id
    FROM public.usuarios
    WHERE android_id = p_android_id AND activo = true
    LIMIT 1;

    IF EXISTS (SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id) THEN
        RETURN;
    END IF;

    IF v_usuario_id IS NOT NULL THEN
        PERFORM set_config('app.usuario_id_actual', v_usuario_id::text, true);
    END IF;

    DELETE FROM public.productos WHERE id = p_id AND local_id = p_local_id;

    INSERT INTO public.acciones_procesadas (accion_id, tipo)
    VALUES (p_accion_id, 'eliminar_producto');
END;
$function$;

-- ---------------------------------------------------------------------------
-- cerrar_turno
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.cerrar_turno(p_android_id text, p_local_id bigint, p_turno_id bigint, p_cierre numeric)
 RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_catalog'
AS $function$
declare
    v_usuario_id bigint;
    v_rol text;
    v_cliente_id uuid;
    v_turno record;
    v_efectivo_vendido numeric;
    v_nuevo_turno_id bigint;
begin
    SELECT u.id, u.rol, u.cliente_id INTO v_usuario_id, v_rol, v_cliente_id
    FROM usuarios u
    WHERE u.android_id = p_android_id AND u.activo = true
    LIMIT 1;

    IF v_rol IS DISTINCT FROM 'admin' THEN
        RAISE EXCEPTION 'Solo un admin puede cerrar el turno del local';
    END IF;

    -- Mismo candado que registrar_venta: mientras se cierra este turno y se
    -- abre el siguiente, ninguna venta puede colarse asignada al turno viejo
    -- justo después de cerrado, ni crear un segundo turno abierto en paralelo.
    PERFORM pg_advisory_xact_lock(hashtext('turno_local_' || p_local_id::text)::bigint);

    -- Antes esto sumaba por rango de horas (created_at >= apertura del
    -- turno). Ahora se suma directo por turno_id: exacto, sin depender de
    -- que el reloj del celular que vendió coincida con el del servidor.
    select coalesce(sum(efectivo), 0) into v_efectivo_vendido
    from ventas where local_id = p_local_id and turno_id = p_turno_id;

    update turnos
    set cierre = p_cierre,
        diferencia = p_cierre - (apertura + v_efectivo_vendido)
    where id = p_turno_id and local_id = p_local_id;

    -- Red de seguridad heredada: con el candado de arriba esto ya no
    -- debería pasar nunca, pero si quedó algún turno abierto suelto de
    -- antes de esta migración, se cierra en vez de dejarlo colgado.
    for v_turno in
        select * from turnos
        where local_id = p_local_id and cierre is null and id <> p_turno_id
    loop
        select coalesce(sum(efectivo), 0) into v_efectivo_vendido
        from ventas where local_id = p_local_id and turno_id = v_turno.id;

        update turnos
        set cierre = v_turno.apertura + v_efectivo_vendido,
            diferencia = 0
        where id = v_turno.id;
    end loop;

    INSERT INTO turnos (local_id, cliente_id, usuario_id, apertura, created_at)
    VALUES (p_local_id, v_cliente_id, v_usuario_id, 0, now())
    RETURNING id INTO v_nuevo_turno_id;

    return v_nuevo_turno_id;
end;
$function$;
