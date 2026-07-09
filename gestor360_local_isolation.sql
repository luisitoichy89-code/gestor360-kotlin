-- ==================================================================
-- GESTOR360 — AISLAMIENTO COMPLETO POR LOCAL
-- v2.0 — reemplaza el fix_local_id.sql parcial
-- ==================================================================
-- Ejecutar en Supabase: Database → SQL Editor → Run.
-- Es idempotente: se puede volver a correr sin efectos secundarios
-- (CREATE OR REPLACE, ADD COLUMN IF NOT EXISTS).
--
-- LÓGICA DE CONTEXTO (aplica a TODAS las funciones):
--   Vendedor  → usuarios.local_id IS NOT NULL  → solo ve SU local
--   Admin     → usuarios.local_id IS NULL      → ve TODOS los locales del cliente
--   Dispositivo licenciado → licencias.local_id  → ve el local de la licencia
-- ==================================================================


-- ──────────────────────────────────────────────────────────────────
-- PASO 1 · MIGRACIONES DE ESQUEMA
-- ──────────────────────────────────────────────────────────────────
-- Cualquier columna que ya exista es ignorada silenciosamente.

ALTER TABLE licencias            ADD COLUMN IF NOT EXISTS local_id BIGINT REFERENCES locales(id);
ALTER TABLE productos            ADD COLUMN IF NOT EXISTS local_id BIGINT REFERENCES locales(id);
ALTER TABLE ventas               ADD COLUMN IF NOT EXISTS local_id BIGINT REFERENCES locales(id);
ALTER TABLE ventas               ADD COLUMN IF NOT EXISTS android_id TEXT;
ALTER TABLE tarjetas             ADD COLUMN IF NOT EXISTS local_id BIGINT REFERENCES locales(id);
ALTER TABLE mermas_pendientes    ADD COLUMN IF NOT EXISTS local_id BIGINT REFERENCES locales(id);
ALTER TABLE turnos               ADD COLUMN IF NOT EXISTS local_id BIGINT REFERENCES locales(id);

-- Tablas del flujo de aprobaciones (créalas si no existen)
CREATE TABLE IF NOT EXISTS solicitudes_producto (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre      TEXT NOT NULL,
    precio      FLOAT8 NOT NULL,
    stock       FLOAT8 NOT NULL DEFAULT 0,
    solicitante_android_id TEXT,
    local_id    BIGINT REFERENCES locales(id),
    cliente_id  TEXT,
    estado      TEXT NOT NULL DEFAULT 'pendiente', -- pendiente | aprobada | rechazada
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS solicitudes_stock (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id BIGINT REFERENCES productos(id),
    cantidad    FLOAT8 NOT NULL,
    solicitante_android_id TEXT,
    local_id    BIGINT REFERENCES locales(id),
    cliente_id  TEXT,
    estado      TEXT NOT NULL DEFAULT 'pendiente',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE solicitudes_producto  ADD COLUMN IF NOT EXISTS local_id BIGINT REFERENCES locales(id);
ALTER TABLE solicitudes_stock     ADD COLUMN IF NOT EXISTS local_id BIGINT REFERENCES locales(id);

-- Índices: mejoran rendimiento en todas las consultas filtradas por local
CREATE INDEX IF NOT EXISTS idx_usuarios_android     ON usuarios(android_id) WHERE activo = true;
CREATE INDEX IF NOT EXISTS idx_licencias_device     ON licencias(device_id) WHERE activo = true;
CREATE INDEX IF NOT EXISTS idx_productos_local      ON productos(local_id, cliente_id);
CREATE INDEX IF NOT EXISTS idx_ventas_local         ON ventas(local_id, cliente_id);
CREATE INDEX IF NOT EXISTS idx_tarjetas_local       ON tarjetas(local_id, cliente_id) WHERE activo = true;
CREATE INDEX IF NOT EXISTS idx_mermas_local         ON mermas_pendientes(local_id, cliente_id) WHERE NOT resuelta;
CREATE INDEX IF NOT EXISTS idx_turnos_local         ON turnos(local_id, cliente_id);


-- ──────────────────────────────────────────────────────────────────
-- PASO 2 · FUNCIÓN AUXILIAR DE CONTEXTO
-- ──────────────────────────────────────────────────────────────────
-- Centraliza "¿quién es este android_id y a qué local pertenece?".
-- Todas las RPCs la usan en lugar de repetir el mismo CTE.
--
--  cliente_id → identifica el negocio
--  local_id   → NULL = admin (sin filtro de local)
--               NOT NULL = vendedor / dispositivo anclado a un local
--  rol        → 'admin' | 'seller' | 'device'

CREATE OR REPLACE FUNCTION _ctx(p_android_id TEXT)
RETURNS TABLE(cliente_id TEXT, local_id BIGINT, rol TEXT)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    -- Camino 1: usuario registrado (cajero o admin)
    SELECT u.cliente_id::TEXT, u.local_id, u.rol
    FROM   usuarios u
    WHERE  u.android_id = p_android_id AND u.activo = true
    LIMIT  1

    UNION ALL

    -- Camino 2 (fallback): dispositivo licenciado sin usuario propio
    SELECT l.cliente_id::TEXT, l.local_id, 'device'::TEXT
    FROM   licencias l
    WHERE  l.device_id = p_android_id AND l.activo = true
      AND  NOT EXISTS (
               SELECT 1 FROM usuarios u
               WHERE  u.android_id = p_android_id AND u.activo = true
           )
    LIMIT  1;
$$;


-- ──────────────────────────────────────────────────────────────────
-- PASO 3 · RPCs DE LECTURA
-- ──────────────────────────────────────────────────────────────────

-- ·· 3.1 Locales disponibles ········································
-- Admin → todos los locales del cliente.
-- Vendedor → solo su propio local (la barra SelectorDeLocalBar
--   en la app solo se muestra cuando hay > 1 local, así no aparece).
CREATE OR REPLACE FUNCTION public.get_locales(p_android_id TEXT)
RETURNS SETOF locales
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    SELECT l.*
    FROM   locales l
    JOIN   _ctx(p_android_id) c ON l.cliente_id::TEXT = c.cliente_id
    WHERE  (c.local_id IS NULL OR l.id = c.local_id)
    ORDER  BY l.nombre;
$$;


-- ·· 3.2 Productos ··················································
CREATE OR REPLACE FUNCTION public.get_productos(p_android_id TEXT)
RETURNS SETOF productos
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    SELECT p.*
    FROM   productos p
    JOIN   _ctx(p_android_id) c ON p.cliente_id::TEXT = c.cliente_id
    WHERE  (c.local_id IS NULL OR p.local_id = c.local_id)
    ORDER  BY p.nombre;
$$;


-- ·· 3.3 Ventas ·····················································
CREATE OR REPLACE FUNCTION public.get_ventas(p_android_id TEXT)
RETURNS SETOF ventas
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    SELECT v.*
    FROM   ventas v
    JOIN   _ctx(p_android_id) c ON v.cliente_id::TEXT = c.cliente_id
    WHERE  (c.local_id IS NULL OR v.local_id = c.local_id)
    ORDER  BY v.created_at DESC;
$$;


-- ·· 3.4 Tarjetas ···················································
CREATE OR REPLACE FUNCTION public.get_tarjetas(p_android_id TEXT)
RETURNS SETOF tarjetas
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    SELECT t.*
    FROM   tarjetas t
    JOIN   _ctx(p_android_id) c ON t.cliente_id::TEXT = c.cliente_id
    WHERE  t.activo = true
      AND  (c.local_id IS NULL OR t.local_id = c.local_id)
    ORDER  BY t.banco;
$$;


-- ·· 3.5 Mermas pendientes ···········································
CREATE OR REPLACE FUNCTION public.get_mermas_pendientes(p_android_id TEXT)
RETURNS SETOF mermas_pendientes
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    SELECT m.*
    FROM   mermas_pendientes m
    JOIN   _ctx(p_android_id) c ON m.cliente_id::TEXT = c.cliente_id
    WHERE  NOT m.resuelta
      AND  (c.local_id IS NULL OR m.local_id = c.local_id)
    ORDER  BY m.created_at DESC;
$$;


-- ·· 3.6 Turno activo del dispositivo ·······························
-- No usa fallback a licencias: un turno es personal al usuario.
CREATE OR REPLACE FUNCTION public.obtener_turno_activo(p_android_id TEXT)
RETURNS SETOF turnos
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    SELECT t.*
    FROM   turnos t
    JOIN   usuarios u ON u.id = t.usuario_id
    WHERE  u.android_id = p_android_id
      AND  u.activo = true
      AND  t.cierre IS NULL
    ORDER  BY t.created_at DESC
    LIMIT  1;
$$;


-- ·· 3.7 Historial de turnos (cierre de caja) ·······················
CREATE OR REPLACE FUNCTION public.get_turnos(p_android_id TEXT)
RETURNS SETOF turnos
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    SELECT t.*
    FROM   turnos t
    JOIN   _ctx(p_android_id) c ON t.cliente_id::TEXT = c.cliente_id
    WHERE  (c.local_id IS NULL OR t.local_id = c.local_id)
    ORDER  BY t.created_at DESC
    LIMIT  100;
$$;


-- ·· 3.8 Solicitudes de productos pendientes (aprobaciones) ·········
CREATE OR REPLACE FUNCTION public.get_solicitudes_producto(p_android_id TEXT)
RETURNS SETOF solicitudes_producto
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    SELECT sp.*
    FROM   solicitudes_producto sp
    JOIN   _ctx(p_android_id) c ON sp.cliente_id::TEXT = c.cliente_id
    WHERE  sp.estado = 'pendiente'
      AND  (c.local_id IS NULL OR sp.local_id = c.local_id)
    ORDER  BY sp.created_at DESC;
$$;


-- ·· 3.9 Solicitudes de aumento de stock pendientes ·················
CREATE OR REPLACE FUNCTION public.get_solicitudes_stock(p_android_id TEXT)
RETURNS SETOF solicitudes_stock
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
    SELECT ss.*
    FROM   solicitudes_stock ss
    JOIN   _ctx(p_android_id) c ON ss.cliente_id::TEXT = c.cliente_id
    WHERE  ss.estado = 'pendiente'
      AND  (c.local_id IS NULL OR ss.local_id = c.local_id)
    ORDER  BY ss.created_at DESC;
$$;


-- ──────────────────────────────────────────────────────────────────
-- PASO 4 · RPCs DE ESCRITURA
-- ──────────────────────────────────────────────────────────────────

-- ·· 4.1 Registrar venta ············································
-- p_local_id es opcional: si el admin lo manda (desde SelectorDeLocalBar)
-- se usa ese valor; si viene NULL se usa el local del vendedor.
CREATE OR REPLACE FUNCTION public.registrar_venta(
    p_android_id    TEXT,
    p_producto_id   BIGINT,
    p_cantidad      FLOAT8,
    p_total         FLOAT8,
    p_metodo        TEXT,
    p_efectivo      FLOAT8 DEFAULT 0,
    p_transferencia FLOAT8 DEFAULT 0,
    p_cliente_ci    TEXT   DEFAULT '',
    p_cliente_tel   TEXT   DEFAULT '',
    p_cliente_nombre TEXT  DEFAULT '',
    p_local_id      BIGINT DEFAULT NULL   -- admin puede especificar el local
)
RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx        RECORD;
    v_local_id   BIGINT;
    v_usuario_id BIGINT;
    v_venta_id   UUID;
    v_nombre     TEXT;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN
        RAISE EXCEPTION 'Dispositivo no autorizado: %', p_android_id;
    END IF;

    -- El local efectivo: parámetro explícito > sesión de usuario > error
    v_local_id := COALESCE(p_local_id, v_ctx.local_id);
    IF v_local_id IS NULL THEN
        RAISE EXCEPTION 'No se pudo determinar el local para esta venta. El admin debe seleccionar un local.';
    END IF;

    SELECT id INTO v_usuario_id FROM usuarios WHERE android_id = p_android_id AND activo = true LIMIT 1;
    SELECT nombre INTO v_nombre FROM productos WHERE id = p_producto_id LIMIT 1;

    -- Descontar stock
    UPDATE productos
    SET    stock = stock - p_cantidad
    WHERE  id = p_producto_id
      AND  cliente_id::TEXT = v_ctx.cliente_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Producto no encontrado o no pertenece a este cliente';
    END IF;

    -- Registrar venta
    INSERT INTO ventas (
        producto_id, producto_nombre, cantidad, total, metodo,
        efectivo, transferencia, usuario_id, local_id, cliente_id,
        cliente_ci, cliente_tel, cliente_nombre, android_id
    ) VALUES (
        p_producto_id, v_nombre, p_cantidad, p_total, p_metodo,
        p_efectivo, p_transferencia, v_usuario_id, v_local_id, v_ctx.cliente_id,
        NULLIF(p_cliente_ci,''), NULLIF(p_cliente_tel,''), NULLIF(p_cliente_nombre,''),
        p_android_id
    )
    RETURNING id INTO v_venta_id;

    RETURN v_venta_id;
END;
$$;


-- ·· 4.2 Crear producto ·············································
-- p_local_id opcional: si el admin no lo manda, se usa su local en sesión.
-- Un vendedor siempre hereda su local automáticamente.
CREATE OR REPLACE FUNCTION public.crear_producto(
    p_android_id TEXT,
    p_nombre     TEXT,
    p_precio     FLOAT8,
    p_stock      FLOAT8,
    p_ubicacion  TEXT   DEFAULT '',
    p_categoria  TEXT   DEFAULT '',
    p_almacen_id TEXT   DEFAULT NULL,  -- legacy, ignorado en lógica nueva
    p_local_id   BIGINT DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx      RECORD;
    v_local_id BIGINT;
    v_id       BIGINT;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN
        RAISE EXCEPTION 'Dispositivo no autorizado: %', p_android_id;
    END IF;

    v_local_id := COALESCE(p_local_id, v_ctx.local_id);
    IF v_local_id IS NULL THEN
        RAISE EXCEPTION 'Especifica el local al que pertenece el producto.';
    END IF;

    INSERT INTO productos (nombre, precio, stock, ubicacion, categoria, almacen_id, local_id, cliente_id)
    VALUES (upper(trim(p_nombre)), p_precio, p_stock, upper(trim(p_ubicacion)), trim(p_categoria), p_almacen_id, v_local_id, v_ctx.cliente_id)
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;


-- ·· 4.3 Actualizar producto ·········································
-- Verifica que el producto pertenezca al cliente (y al local si es vendedor).
CREATE OR REPLACE FUNCTION public.actualizar_producto(
    p_android_id TEXT,
    p_id         BIGINT,
    p_nombre     TEXT,
    p_precio     FLOAT8,
    p_stock      FLOAT8,
    p_ubicacion  TEXT DEFAULT '',
    p_categoria  TEXT DEFAULT ''
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx RECORD;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF;

    UPDATE productos
    SET    nombre    = upper(trim(p_nombre)),
           precio    = p_precio,
           stock     = p_stock,
           ubicacion = upper(trim(p_ubicacion)),
           categoria = trim(p_categoria)
    WHERE  id = p_id
      AND  cliente_id::TEXT = v_ctx.cliente_id
      AND  (v_ctx.local_id IS NULL OR local_id = v_ctx.local_id);

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Producto no encontrado o sin permisos para este local';
    END IF;
END;
$$;


-- ·· 4.4 Eliminar producto ···········································
CREATE OR REPLACE FUNCTION public.eliminar_producto(
    p_android_id TEXT,
    p_id         BIGINT
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx RECORD;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF;

    DELETE FROM productos
    WHERE  id = p_id
      AND  cliente_id::TEXT = v_ctx.cliente_id
      AND  (v_ctx.local_id IS NULL OR local_id = v_ctx.local_id);

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Producto no encontrado o sin permisos para este local';
    END IF;
END;
$$;


-- ·· 4.5 Anular venta ················································
CREATE OR REPLACE FUNCTION public.anular_venta(
    p_android_id TEXT,
    p_venta_id   UUID
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx RECORD;
    v    RECORD;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF;

    SELECT * INTO v FROM ventas
    WHERE  id = p_venta_id AND cliente_id::TEXT = v_ctx.cliente_id
      AND  (v_ctx.local_id IS NULL OR local_id = v_ctx.local_id);

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Venta no encontrada o sin permisos para este local';
    END IF;

    -- Devolver stock
    UPDATE productos SET stock = stock + v.cantidad WHERE id = v.producto_id;
    DELETE FROM ventas WHERE id = p_venta_id;
END;
$$;


-- ·· 4.6 Editar tarjeta ··············································
CREATE OR REPLACE FUNCTION public.editar_tarjeta(
    p_android_id TEXT,
    p_id         BIGINT,
    p_banco      TEXT,
    p_numero     TEXT,
    p_titular    TEXT DEFAULT NULL,
    p_activo     BOOLEAN DEFAULT true
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx RECORD;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF;

    UPDATE tarjetas
    SET    banco   = p_banco,
           numero  = p_numero,
           titular = p_titular,
           activo  = p_activo
    WHERE  id = p_id
      AND  cliente_id::TEXT = v_ctx.cliente_id
      AND  (v_ctx.local_id IS NULL OR local_id = v_ctx.local_id);

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Tarjeta no encontrada o sin permisos para este local';
    END IF;
END;
$$;


-- ·· 4.7 Abrir turno ·················································
CREATE OR REPLACE FUNCTION public.abrir_turno(
    p_android_id    TEXT,
    p_monto_apertura FLOAT8 DEFAULT 0
)
RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx        RECORD;
    v_usuario_id BIGINT;
    v_turno_id   BIGINT;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF;
    IF v_ctx.local_id IS NULL THEN RAISE EXCEPTION 'El admin debe seleccionar un local antes de abrir turno'; END IF;

    SELECT id INTO v_usuario_id FROM usuarios WHERE android_id = p_android_id AND activo = true LIMIT 1;

    -- Cerrar turno abierto previo si existe (protección)
    UPDATE turnos SET cierre = now()
    WHERE  usuario_id = v_usuario_id AND cierre IS NULL;

    INSERT INTO turnos (usuario_id, android_id, local_id, cliente_id, monto_apertura)
    VALUES (v_usuario_id, p_android_id, v_ctx.local_id, v_ctx.cliente_id, p_monto_apertura)
    RETURNING id INTO v_turno_id;

    RETURN v_turno_id;
END;
$$;


-- ·· 4.8 Cerrar turno ················································
CREATE OR REPLACE FUNCTION public.cerrar_turno(
    p_android_id  TEXT,
    p_monto_cierre FLOAT8 DEFAULT 0
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_usuario_id BIGINT;
BEGIN
    SELECT id INTO v_usuario_id FROM usuarios WHERE android_id = p_android_id AND activo = true LIMIT 1;
    IF v_usuario_id IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF;

    UPDATE turnos
    SET    cierre       = now(),
           monto_cierre = p_monto_cierre
    WHERE  usuario_id = v_usuario_id AND cierre IS NULL;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No hay turno abierto para este usuario';
    END IF;
END;
$$;


-- ·· 4.9 Solicitar merma (vendedor) ··································
CREATE OR REPLACE FUNCTION public.solicitar_merma(
    p_android_id      TEXT,
    p_producto_id     BIGINT,
    p_producto_nombre TEXT,
    p_cantidad        FLOAT8,
    p_motivo          TEXT DEFAULT ''
)
RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx RECORD;
    v_id  BIGINT;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF;

    INSERT INTO mermas_pendientes
        (producto_id, producto_nombre, cantidad, motivo, solicitante_android_id, local_id, cliente_id)
    VALUES
        (p_producto_id, p_producto_nombre, p_cantidad, p_motivo, p_android_id, v_ctx.local_id, v_ctx.cliente_id)
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;


-- ·· 4.10 Resolver merma (admin) ·····································
-- Solo puede resolver mermas del cliente. Si el admin tiene local_id
-- (caso raro), solo puede resolver las de su local.
CREATE OR REPLACE FUNCTION public.resolver_merma(
    p_android_id TEXT,
    p_merma_id   BIGINT,
    p_aprobada   BOOLEAN
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx  RECORD;
    v_merma RECORD;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL OR v_ctx.rol NOT IN ('admin') THEN
        RAISE EXCEPTION 'Solo el admin puede resolver mermas';
    END IF;

    SELECT * INTO v_merma FROM mermas_pendientes
    WHERE  id = p_merma_id
      AND  cliente_id::TEXT = v_ctx.cliente_id
      AND  NOT resuelta
      AND  (v_ctx.local_id IS NULL OR local_id = v_ctx.local_id);

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Merma no encontrada o ya resuelta';
    END IF;

    IF p_aprobada THEN
        UPDATE productos SET stock = stock - v_merma.cantidad
        WHERE  id = v_merma.producto_id;
    END IF;

    UPDATE mermas_pendientes
    SET    resuelta = true, aprobada = p_aprobada, resolutor_android_id = p_android_id
    WHERE  id = p_merma_id;
END;
$$;


-- ·· 4.11 Solicitar producto nuevo (vendedor) ························
CREATE OR REPLACE FUNCTION public.solicitar_producto(
    p_android_id TEXT,
    p_nombre     TEXT,
    p_precio     FLOAT8,
    p_stock      FLOAT8 DEFAULT 0
)
RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx RECORD;
    v_id  BIGINT;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF;

    INSERT INTO solicitudes_producto
        (nombre, precio, stock, solicitante_android_id, local_id, cliente_id)
    VALUES
        (upper(trim(p_nombre)), p_precio, p_stock, p_android_id, v_ctx.local_id, v_ctx.cliente_id)
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;


-- ·· 4.12 Aprobar solicitud de producto (admin) ······················
CREATE OR REPLACE FUNCTION public.aprobar_solicitud_producto(
    p_android_id    TEXT,
    p_solicitud_id  BIGINT,
    p_aprobada      BOOLEAN
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx RECORD;
    v_sol RECORD;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL OR v_ctx.rol <> 'admin' THEN
        RAISE EXCEPTION 'Solo el admin puede aprobar solicitudes';
    END IF;

    SELECT * INTO v_sol FROM solicitudes_producto
    WHERE  id = p_solicitud_id AND cliente_id::TEXT = v_ctx.cliente_id AND estado = 'pendiente'
      AND  (v_ctx.local_id IS NULL OR local_id = v_ctx.local_id);

    IF NOT FOUND THEN RAISE EXCEPTION 'Solicitud no encontrada'; END IF;

    IF p_aprobada THEN
        INSERT INTO productos (nombre, precio, stock, local_id, cliente_id)
        VALUES (v_sol.nombre, v_sol.precio, v_sol.stock, v_sol.local_id, v_sol.cliente_id);
    END IF;

    UPDATE solicitudes_producto SET estado = CASE WHEN p_aprobada THEN 'aprobada' ELSE 'rechazada' END
    WHERE  id = p_solicitud_id;
END;
$$;


-- ·· 4.13 Solicitar aumento de stock (vendedor) ·····················
CREATE OR REPLACE FUNCTION public.solicitar_aumento_stock(
    p_android_id  TEXT,
    p_producto_id BIGINT,
    p_cantidad    FLOAT8
)
RETURNS BIGINT
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx RECORD;
    v_id  BIGINT;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL THEN RAISE EXCEPTION 'Dispositivo no autorizado'; END IF;

    INSERT INTO solicitudes_stock
        (producto_id, cantidad, solicitante_android_id, local_id, cliente_id)
    VALUES
        (p_producto_id, p_cantidad, p_android_id, v_ctx.local_id, v_ctx.cliente_id)
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;


-- ·· 4.14 Aprobar aumento de stock (admin) ···························
CREATE OR REPLACE FUNCTION public.aprobar_aumento_stock(
    p_android_id   TEXT,
    p_solicitud_id BIGINT,
    p_aprobada     BOOLEAN
)
RETURNS VOID
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO 'public', 'pg_catalog'
AS $$
DECLARE
    v_ctx RECORD;
    v_sol RECORD;
BEGIN
    SELECT * INTO v_ctx FROM _ctx(p_android_id) LIMIT 1;
    IF v_ctx IS NULL OR v_ctx.rol <> 'admin' THEN
        RAISE EXCEPTION 'Solo el admin puede aprobar solicitudes';
    END IF;

    SELECT * INTO v_sol FROM solicitudes_stock
    WHERE  id = p_solicitud_id AND cliente_id::TEXT = v_ctx.cliente_id AND estado = 'pendiente'
      AND  (v_ctx.local_id IS NULL OR local_id = v_ctx.local_id);

    IF NOT FOUND THEN RAISE EXCEPTION 'Solicitud no encontrada'; END IF;

    IF p_aprobada THEN
        UPDATE productos SET stock = stock + v_sol.cantidad
        WHERE  id = v_sol.producto_id AND cliente_id::TEXT = v_ctx.cliente_id;
    END IF;

    UPDATE solicitudes_stock SET estado = CASE WHEN p_aprobada THEN 'aprobada' ELSE 'rechazada' END
    WHERE  id = p_solicitud_id;
END;
$$;


-- ──────────────────────────────────────────────────────────────────
-- PASO 5 · ROW LEVEL SECURITY
-- ──────────────────────────────────────────────────────────────────
-- Todas las funciones son SECURITY DEFINER (corren como el owner del
-- schema, bypassando RLS). Esto es correcto porque la lógica de acceso
-- ya está dentro de cada función mediante _ctx().
--
-- Las tablas directas (SELECT/INSERT/UPDATE/DELETE sin RPC) deben
-- estar protegidas por RLS para que nadie pueda saltarse las funciones.
-- Activa RLS en todas las tablas de datos del negocio:

ALTER TABLE productos          ENABLE ROW LEVEL SECURITY;
ALTER TABLE ventas             ENABLE ROW LEVEL SECURITY;
ALTER TABLE tarjetas           ENABLE ROW LEVEL SECURITY;
ALTER TABLE mermas_pendientes  ENABLE ROW LEVEL SECURITY;
ALTER TABLE turnos             ENABLE ROW LEVEL SECURITY;
ALTER TABLE solicitudes_producto ENABLE ROW LEVEL SECURITY;
ALTER TABLE solicitudes_stock    ENABLE ROW LEVEL SECURITY;

-- Sin políticas permisivas, nadie puede leer/escribir directamente.
-- El acceso es ÚNICAMENTE a través de las funciones SECURITY DEFINER.
-- Si necesitas políticas de servicio (para el dashboard de Supabase),
-- crea una política con role = 'service_role':
--
-- CREATE POLICY "service_role bypass" ON productos
--   USING (auth.role() = 'service_role');


-- ──────────────────────────────────────────────────────────────────
-- PASO 6 · DATOS INICIALES: asignar local_id a registros existentes
-- ──────────────────────────────────────────────────────────────────
-- Si ya tienes datos en producción, necesitas asignar local_id a los
-- registros históricos. Ajusta los IDs según tu base de datos real.
--
-- Ejemplo (descomenta y adapta):
--
-- UPDATE productos  SET local_id = 1 WHERE local_id IS NULL AND cliente_id = 'tu-cliente-id';
-- UPDATE ventas      SET local_id = 1 WHERE local_id IS NULL AND cliente_id = 'tu-cliente-id';
-- UPDATE tarjetas   SET local_id = 1 WHERE local_id IS NULL AND cliente_id = 'tu-cliente-id';
-- UPDATE turnos     SET local_id = 1 WHERE local_id IS NULL AND cliente_id = 'tu-cliente-id';
-- UPDATE mermas_pendientes SET local_id = 1 WHERE local_id IS NULL AND cliente_id = 'tu-cliente-id';
--
-- También asigna local_id a los vendedores en la tabla usuarios:
-- UPDATE usuarios SET local_id = 1 WHERE rol = 'seller' AND local_id IS NULL;
--
-- ──────────────────────────────────────────────────────────────────

