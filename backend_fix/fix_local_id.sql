-- =====================================================================
-- FIX: aislar datos por local_id en vez de cliente_id (todo el negocio)
-- =====================================================================
-- Patrón usado en las 5 funciones: primero se busca el contexto por
-- usuarios.android_id (caso normal: cajero/admin con local propio). Solo
-- si NO existe esa fila, se cae al camino de licencias (ve todo el
-- cliente_id) -- eso cubre el caso hipotético de un dispositivo dueño
-- que no esté también registrado en "usuarios", sin arriesgar dejar a
-- nadie sin acceso. Antes esto se resolvía con UNION ALL + LIMIT 1 SIN
-- ORDER BY, lo cual es no-determinístico en Postgres: podía devolver la
-- fila de "licencias" (todo el negocio) incluso para un vendedor normal.
-- =====================================================================

-- 1) PRODUCTOS
CREATE OR REPLACE FUNCTION public.get_productos(p_android_id text)
 RETURNS SETOF productos
 LANGUAGE sql
 SECURITY DEFINER
AS $function$
  WITH contexto AS (
    SELECT u.cliente_id, u.local_id FROM usuarios u WHERE u.android_id = p_android_id AND u.activo = true LIMIT 1
  ),
  contexto_final AS (
    SELECT cliente_id, local_id FROM contexto
    UNION ALL
    SELECT l.cliente_id, NULL::bigint FROM licencias l
    WHERE l.device_id = p_android_id AND l.activo = true AND NOT EXISTS (SELECT 1 FROM contexto)
    LIMIT 1
  )
  SELECT p.* FROM productos p, contexto_final c
  WHERE p.cliente_id = c.cliente_id AND (c.local_id IS NULL OR p.local_id = c.local_id);
$function$;

-- 2) TARJETAS
-- (antes el primer camino unía por "almacen_id", una columna muerta que
-- no está atada a ninguna tabla real; y el segundo camino, por licencias,
-- mostraba TODAS las tarjetas del negocio sin filtrar local, siempre)
CREATE OR REPLACE FUNCTION public.get_tarjetas(p_android_id text)
 RETURNS SETOF tarjetas
 LANGUAGE sql
 SECURITY DEFINER
AS $function$
  WITH contexto AS (
    SELECT u.cliente_id, u.local_id FROM usuarios u WHERE u.android_id = p_android_id AND u.activo = true LIMIT 1
  ),
  contexto_final AS (
    SELECT cliente_id, local_id FROM contexto
    UNION ALL
    SELECT l.cliente_id, NULL::bigint FROM licencias l
    WHERE l.device_id = p_android_id AND l.activo = true AND NOT EXISTS (SELECT 1 FROM contexto)
    LIMIT 1
  )
  SELECT t.* FROM tarjetas t, contexto_final c
  WHERE t.cliente_id = c.cliente_id AND t.activo = true AND (c.local_id IS NULL OR t.local_id = c.local_id);
$function$;

-- 3) MERMAS PENDIENTES (esta es la que realmente usa la pantalla "Aprobaciones")
-- (antes filtraba solo por cliente_id, ignorando la columna local_id que
-- la tabla ya tiene)
CREATE OR REPLACE FUNCTION public.get_mermas_pendientes(p_android_id text)
 RETURNS SETOF mermas_pendientes
 LANGUAGE sql
 SECURITY DEFINER
AS $function$
  WITH contexto AS (
    SELECT u.cliente_id, u.local_id FROM usuarios u WHERE u.android_id = p_android_id AND u.activo = true LIMIT 1
  ),
  contexto_final AS (
    SELECT cliente_id, local_id FROM contexto
    UNION ALL
    SELECT l.cliente_id, NULL::bigint FROM licencias l
    WHERE l.device_id = p_android_id AND l.activo = true AND NOT EXISTS (SELECT 1 FROM contexto)
    LIMIT 1
  )
  SELECT m.* FROM mermas_pendientes m, contexto_final c
  WHERE m.cliente_id = c.cliente_id AND (c.local_id IS NULL OR m.local_id = c.local_id)
  ORDER BY m.created_at DESC;
$function$;

-- 4) VENTAS (versión de 1 parámetro, la que usa la app para cierre de caja)
CREATE OR REPLACE FUNCTION public.get_ventas(p_android_id text)
 RETURNS SETOF ventas
 LANGUAGE sql
 SECURITY DEFINER
AS $function$
  WITH contexto AS (
    SELECT u.cliente_id, u.local_id FROM usuarios u WHERE u.android_id = p_android_id AND u.activo = true LIMIT 1
  ),
  contexto_final AS (
    SELECT cliente_id, local_id FROM contexto
    UNION ALL
    SELECT l.cliente_id, NULL::bigint FROM licencias l
    WHERE l.device_id = p_android_id AND l.activo = true AND NOT EXISTS (SELECT 1 FROM contexto)
    LIMIT 1
  )
  SELECT v.* FROM ventas v, contexto_final c
  WHERE v.cliente_id = c.cliente_id AND (c.local_id IS NULL OR v.local_id = c.local_id);
$function$;

-- 5) TURNO ACTIVO (el más delicado: antes un cajero podía, por la rama de
-- licencias, terminar viendo -- y cerrando -- el turno abierto de OTRO
-- local. Un turno es siempre personal a un usuario_id, así que aquí se
-- quita la rama de licencias por completo, no se necesita fallback.)
CREATE OR REPLACE FUNCTION public.obtener_turno_activo(p_android_id text)
 RETURNS SETOF turnos
 LANGUAGE sql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_catalog'
AS $function$
  SELECT t.*
  FROM turnos t
  JOIN usuarios u ON u.id = t.usuario_id
  WHERE u.android_id = p_android_id AND u.activo = true
    AND (t.cierre IS NULL OR t.cierre = 0)
  ORDER BY t.created_at DESC, t.id DESC
  LIMIT 1;
$function$;

-- =====================================================================
-- NOTA: no toqué get_turnos, editar_tarjeta ni resolver_merma porque no
-- llegué a ver su definición. Si alguna sigue mostrando datos mezclados
-- entre locales después de correr esto, pásame su definición y la
-- corrijo con el mismo patrón.
--
-- NOTA 2: get_aprobaciones / aprobaciones_stock / solicitar_producto /
-- solicitar_aumento_stock / solicitar_anular_venta NO están conectadas a
-- ninguna pantalla de la app (AprobacionesScreen usa mermas, no esto), así
-- que las dejé como están -- no son la causa del problema que reportaste.
-- =====================================================================
