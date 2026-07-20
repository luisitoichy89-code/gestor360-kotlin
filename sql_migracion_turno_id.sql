-- 1. Agregar turno_id a las 4 tablas
ALTER TABLE public.ventas ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
ALTER TABLE public.mermas ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
ALTER TABLE public.devoluciones ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
ALTER TABLE public.productos_eliminados ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);

-- 2. Función helper para obtener el turno abierto (con bloqueo)
CREATE OR REPLACE FUNCTION public.obtener_turno_abierto(p_local_id bigint)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_turno_id bigint;
BEGIN
    SELECT id INTO v_turno_id FROM turnos WHERE local_id = p_local_id AND cierre IS NULL ORDER BY created_at DESC LIMIT 1 FOR UPDATE;
    RETURN v_turno_id;
END;
$$;

-- 3. registrar_venta con turno_id
CREATE OR REPLACE FUNCTION public.registrar_venta(p_android_id text, p_local_id bigint, p_id text, p_producto_id text, p_cantidad numeric, p_total numeric, p_metodo text, p_efectivo numeric, p_transferencia numeric, p_cliente_ci text DEFAULT ''::text, p_cliente_tel text DEFAULT ''::text, p_cliente_nombre text DEFAULT ''::text, p_tarjeta_id text DEFAULT NULL::text, p_accion_id uuid DEFAULT NULL::uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER
AS $func$
DECLARE
    v_usuario_id bigint;
    v_cliente_id uuid;
    v_turno_id bigint;
BEGIN
    v_usuario_id := public.validar_usuario_venta(p_android_id, p_local_id);
    IF p_accion_id IS NOT NULL AND EXISTS (SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id) THEN RETURN; END IF;
    SELECT cliente_id INTO v_cliente_id FROM locales WHERE id = p_local_id;
    v_turno_id := public.obtener_turno_abierto(p_local_id);
    IF v_turno_id IS NULL THEN
        INSERT INTO turnos (local_id, cliente_id, usuario_id, apertura, created_at) VALUES (p_local_id, v_cliente_id, v_usuario_id, 0, now()) ON CONFLICT DO NOTHING;
        v_turno_id := public.obtener_turno_abierto(p_local_id);
    END IF;
    INSERT INTO public.ventas (id, local_id, producto_id, cantidad, total, metodo, efectivo, transferencia, cliente_ci, cliente_tel, cliente_nombre, tarjeta_id, turno_id, created_at)
    VALUES (p_id, p_local_id, p_producto_id, p_cantidad, p_total, p_metodo, p_efectivo, p_transferencia, NULLIF(p_cliente_ci, ''), NULLIF(p_cliente_tel, ''), NULLIF(p_cliente_nombre, ''), p_tarjeta_id, v_turno_id, now()) ON CONFLICT (id) DO NOTHING;
    IF p_producto_id IS NOT NULL THEN UPDATE public.productos SET stock = stock - p_cantidad WHERE id = p_producto_id::uuid; END IF;
    IF p_accion_id IS NOT NULL THEN INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'registrar_venta') ON CONFLICT (accion_id) DO NOTHING; END IF;
END;
$func$;

-- 4. crear_merma con turno_id
CREATE OR REPLACE FUNCTION public.crear_merma(p_android_id text, p_local_id bigint, p_id uuid, p_producto_id uuid, p_cantidad numeric, p_motivo text, p_accion_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER
AS $func$
DECLARE
    v_user_id bigint;
    v_user_nombre text;
    v_producto_nombre text;
    v_turno_id bigint;
BEGIN
    SELECT id, nombre INTO v_user_id, v_user_nombre FROM public.usuarios WHERE android_id = p_android_id AND activo = true LIMIT 1;
    IF v_user_id IS NULL THEN RAISE EXCEPTION 'Usuario no autorizado'; END IF;
    IF EXISTS (SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id) THEN RETURN; END IF;
    SELECT nombre INTO v_producto_nombre FROM public.productos WHERE id = p_producto_id;
    v_turno_id := public.obtener_turno_abierto(p_local_id);
    INSERT INTO public.mermas (id, local_id, producto_id, producto_nombre, cantidad, motivo, solicitado_por, solicitado_por_nombre, turno_id, estado)
    VALUES (p_id, p_local_id, p_producto_id, COALESCE(v_producto_nombre, ''), p_cantidad, p_motivo, v_user_id, v_user_nombre, v_turno_id, 'pendiente') ON CONFLICT (id) DO NOTHING;
    INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'crear_merma');
END;
$func$;

-- 5. solicitar_devolucion con turno_id
CREATE OR REPLACE FUNCTION public.solicitar_devolucion(p_android_id text, p_local_id bigint, p_producto_id uuid, p_cantidad numeric, p_metodo text, p_motivo text, p_id uuid, p_accion_id uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO 'public', 'pg_catalog'
AS $func$
declare
    v_user_id bigint;
    v_cliente_id uuid;
    v_usuario_nombre text;
    v_producto_nombre text;
    v_turno_id bigint;
begin
    v_user_id := public.validar_usuario_local(p_android_id, p_local_id);
    IF EXISTS (SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id) THEN RETURN; END IF;
    SELECT u.cliente_id, u.nombre INTO v_cliente_id, v_usuario_nombre FROM public.usuarios u WHERE u.android_id = p_android_id AND u.activo = true LIMIT 1;
    IF v_cliente_id IS NULL THEN SELECT l.cliente_id INTO v_cliente_id FROM public.licencias l WHERE l.device_id = p_android_id AND l.activo = true LIMIT 1; IF v_cliente_id IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF; v_usuario_nombre := ''; END IF;
    SELECT nombre INTO v_producto_nombre FROM public.productos WHERE id = p_producto_id AND local_id = p_local_id;
    IF v_producto_nombre IS NULL THEN RAISE EXCEPTION 'Producto no existe'; END IF;
    v_turno_id := public.obtener_turno_abierto(p_local_id);
    INSERT INTO public.devoluciones (cliente_id, local_id, producto_id, producto_nombre, cantidad, metodo, motivo, solicitado_por, solicitado_por_nombre, turno_id, estado)
    VALUES (v_cliente_id, p_local_id, p_producto_id, v_producto_nombre, p_cantidad, p_metodo, p_motivo, v_user_id, v_usuario_nombre, v_turno_id, 'pendiente');
    INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'solicitar_devolucion');
END;
$func$;
