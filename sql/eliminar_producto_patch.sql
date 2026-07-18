-- ============================================================================
-- eliminar_producto — patch para que productos_eliminados.eliminado_por
-- deje de quedar siempre NULL.
-- ============================================================================
-- Se agrega: resolver v_usuario_id a partir de p_android_id (mismo patrón
-- que get_inventario_dia) y setear la sesión ANTES del DELETE — el trigger
-- productos_after_delete (ver productos_eliminados_setup.sql) lee esa
-- variable de sesión para llenar "eliminado_por".
-- ============================================================================

CREATE OR REPLACE FUNCTION public.eliminar_producto(p_android_id text, p_local_id bigint, p_id uuid, p_accion_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_catalog'
AS $function$
DECLARE
    v_usuario_id bigint;
BEGIN
    IF EXISTS (SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id) THEN RETURN; END IF;

    SELECT u.id INTO v_usuario_id
    FROM public.usuarios u
    WHERE u.android_id = p_android_id AND u.activo = true
    LIMIT 1;

    -- El trigger productos_after_delete lee esta variable de sesión para
    -- llenar productos_eliminados.eliminado_por. Si no hay usuario resuelto
    -- (v_usuario_id NULL), set_config igual corre y el trigger inserta NULL,
    -- que es el mismo comportamiento de antes.
    PERFORM set_config('app.usuario_id_actual', coalesce(v_usuario_id::text, ''), true);

    DELETE FROM public.productos WHERE id = p_id AND local_id = p_local_id;
    INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'eliminar_producto');
END;
$function$;
