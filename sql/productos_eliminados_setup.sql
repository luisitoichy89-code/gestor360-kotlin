-- ============================================================================
-- productos_eliminados — tabla nueva, no existía.
-- ============================================================================
-- Enfoque: en vez de modificar el RPC de borrado (no lo tengo, y así queda
-- a prueba de que en el futuro haya OTRA vía de borrado que se te olvide
-- actualizar), se captura con un trigger AFTER DELETE sobre "productos".
-- Cualquier borrado, venga de donde venga, queda registrado.
--
-- id se reusa igual al id del producto borrado (uuid) — así un retry de
-- sincronización del mismo borrado (típico en cola offline) no duplica fila
-- (ON CONFLICT DO NOTHING).
--
-- PENDIENTE — "eliminado_por" solo se llena si tu RPC de borrado (el que no
-- tengo) hace, ANTES del DELETE:
--     perform set_config('app.usuario_id_actual', v_usuario_id::text, true);
-- Sin esa línea, la columna queda NULL (no rompe nada, solo no sabrás quién
-- borró). Si me pasas ese RPC te dejo esa línea agregada.
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.productos_eliminados (
    id uuid PRIMARY KEY,
    local_id bigint NOT NULL,
    nombre text NOT NULL,
    stock numeric,
    precio numeric,
    eliminado_por bigint REFERENCES public.usuarios(id),
    eliminado_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_productos_eliminados_local_fecha
    ON public.productos_eliminados (local_id, eliminado_at);

CREATE OR REPLACE FUNCTION public.trg_registrar_producto_eliminado()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_catalog'
AS $function$
begin
    insert into productos_eliminados (id, local_id, nombre, stock, precio, eliminado_por, eliminado_at)
    values (
        old.id, old.local_id, old.nombre, old.stock, old.precio,
        nullif(current_setting('app.usuario_id_actual', true), '')::bigint,
        now()
    )
    on conflict (id) do nothing;
    return old;
end;
$function$;

DROP TRIGGER IF EXISTS productos_after_delete ON public.productos;
CREATE TRIGGER productos_after_delete
    AFTER DELETE ON public.productos
    FOR EACH ROW
    EXECUTE FUNCTION public.trg_registrar_producto_eliminado();

GRANT SELECT ON public.productos_eliminados TO anon, authenticated;
