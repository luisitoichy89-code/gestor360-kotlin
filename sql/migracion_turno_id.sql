-- =====================================================================
-- MIGRACIÓN: turno_id explícito en ventas, mermas, devoluciones,
-- productos_eliminados. Reemplaza la inferencia por comparación de
-- timestamps (frágil) por una FK directa a turnos(id).
--
-- Sin backfill: los registros existentes quedan con turno_id = NULL.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) ALTER TABLE: agregar columna turno_id + FK + índice
-- ---------------------------------------------------------------------

ALTER TABLE public.ventas
    ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
CREATE INDEX IF NOT EXISTS idx_ventas_turno_id ON public.ventas(turno_id);

ALTER TABLE public.mermas
    ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
CREATE INDEX IF NOT EXISTS idx_mermas_turno_id ON public.mermas(turno_id);

ALTER TABLE public.devoluciones
    ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
CREATE INDEX IF NOT EXISTS idx_devoluciones_turno_id ON public.devoluciones(turno_id);

-- NOTA: no tengo la definición de la tabla "productos_eliminados" (no vino
-- en los RPCs consultados). Si el nombre real de la tabla es distinto,
-- ajusta este bloque antes de correrlo.
ALTER TABLE public.productos_eliminados
    ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
CREATE INDEX IF NOT EXISTS idx_productos_eliminados_turno_id ON public.productos_eliminados(turno_id);


-- ---------------------------------------------------------------------
-- 2) Helper: obtiene el turno abierto del local con lock (FOR UPDATE)
--    para evitar condición de carrera si dos ventas casi simultáneas
--    intentan crear el turno al mismo tiempo. Si no hay turno abierto,
--    lo crea y devuelve su id.
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.obtener_o_crear_turno_abierto(
    p_local_id bigint,
    p_usuario_id bigint,
    p_cliente_id uuid
) RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_turno_id bigint;
BEGIN
    -- Lock de fila: si dos transacciones concurrentes llegan acá para el
    -- mismo local, la segunda espera a que la primera termine (commit/rollback)
    -- antes de leer, así nunca se crean dos turnos abiertos para el mismo local.
    -- ORDER BY created_at ASC (no por id): así coincide con el criterio que
    -- ya usa get_inventario_dia para elegir "el" turno activo cuando por
    -- algún motivo hubiera más de uno abierto para el mismo local.
    SELECT id INTO v_turno_id
    FROM public.turnos
    WHERE local_id = p_local_id AND cierre IS NULL
    ORDER BY created_at ASC
    LIMIT 1
    FOR UPDATE;

    IF v_turno_id IS NOT NULL THEN
        RETURN v_turno_id;
    END IF;

    INSERT INTO public.turnos (local_id, cliente_id, usuario_id, apertura, created_at)
    VALUES (p_local_id, p_cliente_id, p_usuario_id, 0, now())
    RETURNING id INTO v_turno_id;

    RETURN v_turno_id;
END;
$function$;


-- ---------------------------------------------------------------------
-- 2.1) obtener_turno_abierto: variante SOLO LECTURA, sin crear turno si
--      no hay uno abierto. La necesita el trigger fn_registrar_producto_eliminado
--      (ya existe en tu base, ver CREATE TRIGGER trg_productos_eliminados),
--      que la llama dentro de un BEGIN/EXCEPTION WHEN OTHERS — es decir,
--      si esta función no existe, el trigger de todos modos no falla, pero
--      graba turno_id = NULL siempre en silencio. Con esta función creada,
--      el trigger empieza a funcionar tal cual está, sin tocarlo.
--
--      A propósito NO crea un turno nuevo si no hay uno abierto: borrar un
--      producto no debería disparar la apertura de un turno. Si no hay
--      turno abierto, simplemente queda turno_id = NULL en productos_eliminados
--      (igual que cualquier registro viejo pre-migración).
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.obtener_turno_abierto(
    p_local_id bigint
) RETURNS bigint
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_turno_id bigint;
BEGIN
    SELECT id INTO v_turno_id
    FROM public.turnos
    WHERE local_id = p_local_id AND cierre IS NULL
    ORDER BY created_at ASC
    LIMIT 1;

    RETURN v_turno_id;
END;
$function$;


-- ---------------------------------------------------------------------
-- 3) registrar_venta: usa el helper (con lock) en vez de la creación
--    manual con ON CONFLICT DO NOTHING (que no garantizaba un id de
--    vuelta bajo concurrencia), y graba turno_id en la venta.
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.registrar_venta(
    p_android_id text, p_local_id bigint, p_id text, p_producto_id text,
    p_cantidad numeric, p_total numeric, p_metodo text, p_efectivo numeric,
    p_transferencia numeric, p_cliente_ci text DEFAULT ''::text,
    p_cliente_tel text DEFAULT ''::text, p_cliente_nombre text DEFAULT ''::text,
    p_tarjeta_id text DEFAULT NULL::text, p_accion_id uuid DEFAULT NULL::uuid
) RETURNS void
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

    SELECT cliente_id INTO v_cliente_id FROM turnos WHERE local_id = p_local_id LIMIT 1;
    IF v_cliente_id IS NULL THEN
        SELECT cliente_id INTO v_cliente_id FROM locales WHERE id = p_local_id;
    END IF;

    v_turno_id := public.obtener_o_crear_turno_abierto(p_local_id, v_usuario_id, v_cliente_id);

    INSERT INTO public.ventas (id, local_id, producto_id, cantidad, total, metodo, efectivo, transferencia, cliente_ci, cliente_tel, cliente_nombre, tarjeta_id, turno_id, created_at)
    VALUES (p_id, p_local_id, p_producto_id, p_cantidad, p_total, p_metodo, p_efectivo, p_transferencia, NULLIF(p_cliente_ci, ''), NULLIF(p_cliente_tel, ''), NULLIF(p_cliente_nombre, ''), p_tarjeta_id, v_turno_id, now())
    ON CONFLICT (id) DO NOTHING;

    IF p_producto_id IS NOT NULL THEN
        UPDATE public.productos SET stock = stock - p_cantidad WHERE id = p_producto_id::uuid;
    END IF;

    IF p_accion_id IS NOT NULL THEN
        INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'registrar_venta') ON CONFLICT (accion_id) DO NOTHING;
    END IF;
END;
$function$;

-- NOTA sobre el cambio de v_cliente_id arriba: la versión original leía
-- cliente_id de "locales". Aquí primero intento leerlo de un turno
-- existente del local (más barato si ya hay uno) y si no, caigo a
-- "locales" igual que antes. Si tu tabla "locales" es la fuente de verdad
-- y prefieres mantenerlo simple, borra el primer SELECT y deja solo el que
-- consulta "locales" — no afecta turno_id, es solo de dónde sale cliente_id
-- al crear el turno nuevo.


-- ---------------------------------------------------------------------
-- 4) crear_merma: agrega turno_id. Necesita local_id -> cliente_id, que
--    la función original no resolvía (no lo necesitaba hasta ahora).
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.crear_merma(
    p_android_id text, p_local_id bigint, p_id uuid, p_producto_id uuid,
    p_cantidad numeric, p_motivo text, p_accion_id uuid
) RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
AS $function$
DECLARE
    v_user_id bigint;
    v_user_nombre text;
    v_producto_nombre text;
    v_cliente_id uuid;
    v_turno_id bigint;
BEGIN
    SELECT id, nombre INTO v_user_id, v_user_nombre FROM public.usuarios WHERE android_id = p_android_id AND activo = true LIMIT 1;
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Usuario no autorizado';
    END IF;
    IF EXISTS (SELECT 1 FROM public.acciones_procesadas WHERE accion_id = p_accion_id) THEN RETURN; END IF;

    SELECT nombre INTO v_producto_nombre FROM public.productos WHERE id = p_producto_id;
    SELECT cliente_id INTO v_cliente_id FROM public.locales WHERE id = p_local_id;
    v_turno_id := public.obtener_o_crear_turno_abierto(p_local_id, v_user_id, v_cliente_id);

    INSERT INTO public.mermas (id, local_id, producto_id, producto_nombre, cantidad, motivo, solicitado_por, solicitado_por_nombre, estado, turno_id)
    VALUES (p_id, p_local_id, p_producto_id, COALESCE(v_producto_nombre, ''), p_cantidad, p_motivo, v_user_id, v_user_nombre, 'pendiente', v_turno_id)
    ON CONFLICT (id) DO NOTHING;
    INSERT INTO public.acciones_procesadas (accion_id, tipo) VALUES (p_accion_id, 'crear_merma');
END;
$function$;


-- ---------------------------------------------------------------------
-- 5) solicitar_devolucion: agrega turno_id (ya resolvía v_cliente_id).
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.solicitar_devolucion(
    p_android_id text, p_local_id bigint, p_producto_id uuid, p_cantidad numeric,
    p_metodo text, p_motivo text, p_id uuid, p_accion_id uuid
) RETURNS void
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

    v_turno_id := public.obtener_o_crear_turno_abierto(p_local_id, v_user_id, v_cliente_id);

    INSERT INTO public.devoluciones (
        cliente_id, local_id, producto_id, producto_nombre, cantidad, metodo, motivo,
        solicitado_por, solicitado_por_nombre, estado, turno_id
    )
    VALUES (
        v_cliente_id, p_local_id, p_producto_id, v_producto_nombre, p_cantidad, p_metodo, p_motivo,
        v_user_id, v_usuario_nombre, 'pendiente', v_turno_id
    );

    INSERT INTO public.acciones_procesadas (accion_id, tipo)
    VALUES (p_accion_id, 'solicitar_devolucion');
END;
$function$;


-- =====================================================================
-- get_inventario_dia: NO SE TOCA.
--
-- La versión que me pasaste ya está escrita esperando turno_id (usa
-- v.turno_id, m.turno_id, d.turno_id, pe.turno_id en los WHERE, y ya
-- resuelve el fallback pedido en el punto 3: si p_turno_ids es NULL y la
-- fecha es HOY, usa el turno abierto actual; si es un día pasado, no
-- filtra por turno y muestra todo el día). Con el ALTER TABLE de arriba
-- corriendo, esta función queda funcional tal cual está — no hacía falta
-- reescribirla, solo que las columnas existieran.
-- =====================================================================


-- =====================================================================
-- eliminar_producto y el trigger trg_productos_eliminados: NO SE TOCAN.
--
-- eliminar_producto solo hace el DELETE; el trigger existente
-- (fn_registrar_producto_eliminado) ya inserta en productos_eliminados con
-- turno_id, llamando a obtener_turno_abierto() — la función que se crea
-- arriba en el punto 2.1. Antes de que existiera, el trigger atrapaba el
-- error en su propio BEGIN/EXCEPTION y grababa turno_id = NULL en
-- silencio; con obtener_turno_abierto() ya creada, empieza a funcionar sin
-- tocar ni el trigger ni eliminar_producto.
-- =====================================================================


-- =====================================================================
-- Con esto, Fase 1 queda completa: las 4 tablas tienen turno_id, las 3
-- escrituras (registrar_venta, crear_merma, solicitar_devolucion) lo
-- graban creando turno si hace falta, el trigger de productos eliminados
-- lo graba sin crear turno, y get_inventario_dia ya lo consume tal cual
-- estaba escrita. Corre este archivo completo en el SQL editor de
-- Supabase, de arriba a abajo, en una sola pasada.
-- =====================================================================
