-- ============================================================================
-- PASO 2 de 3 — BACKFILL (correr UNA sola vez, después del PASO 1)
--
-- Llena turno_id/turno_id_resuelto en todo lo que ya existe en la base,
-- usando la misma lógica de rango de horas que usaba get_inventario_dia
-- hasta ahora. De acá en adelante nunca más hace falta este cálculo: las
-- funciones del PASO 3 lo estampan directo al crear cada fila.
--
-- Es seguro correrlo más de una vez (los WHERE solo tocan lo que todavía
-- está en NULL), pero con una vez alcanza.
-- ============================================================================

-- VENTAS
UPDATE public.ventas v
SET turno_id = t.id
FROM public.turnos t
WHERE v.turno_id IS NULL
  AND v.local_id = t.local_id
  AND v.created_at >= t.created_at
  AND v.created_at < COALESCE(
      (SELECT MIN(t2.created_at) FROM public.turnos t2 WHERE t2.local_id = t.local_id AND t2.created_at > t.created_at),
      'infinity'::timestamptz
  );

-- MERMAS (turno en que se pidieron)
UPDATE public.mermas m
SET turno_id = t.id
FROM public.turnos t
WHERE m.turno_id IS NULL
  AND m.local_id = t.local_id
  AND m.created_at >= t.created_at
  AND m.created_at < COALESCE(
      (SELECT MIN(t2.created_at) FROM public.turnos t2 WHERE t2.local_id = t.local_id AND t2.created_at > t.created_at),
      'infinity'::timestamptz
  );

-- MERMAS (turno en que se resolvieron, solo las ya resueltas)
UPDATE public.mermas m
SET turno_id_resuelto = t.id
FROM public.turnos t
WHERE m.turno_id_resuelto IS NULL
  AND m.resuelto_at IS NOT NULL
  AND m.local_id = t.local_id
  AND m.resuelto_at >= t.created_at
  AND m.resuelto_at < COALESCE(
      (SELECT MIN(t2.created_at) FROM public.turnos t2 WHERE t2.local_id = t.local_id AND t2.created_at > t.created_at),
      'infinity'::timestamptz
  );

-- DEVOLUCIONES (turno en que se pidieron)
UPDATE public.devoluciones d
SET turno_id = t.id
FROM public.turnos t
WHERE d.turno_id IS NULL
  AND d.local_id = t.local_id
  AND d.created_at >= t.created_at
  AND d.created_at < COALESCE(
      (SELECT MIN(t2.created_at) FROM public.turnos t2 WHERE t2.local_id = t.local_id AND t2.created_at > t.created_at),
      'infinity'::timestamptz
  );

-- DEVOLUCIONES (turno en que se resolvieron)
UPDATE public.devoluciones d
SET turno_id_resuelto = t.id
FROM public.turnos t
WHERE d.turno_id_resuelto IS NULL
  AND d.resuelto_at IS NOT NULL
  AND d.local_id = t.local_id
  AND d.resuelto_at >= t.created_at
  AND d.resuelto_at < COALESCE(
      (SELECT MIN(t2.created_at) FROM public.turnos t2 WHERE t2.local_id = t.local_id AND t2.created_at > t.created_at),
      'infinity'::timestamptz
  );

-- PRODUCTOS ELIMINADOS
UPDATE public.productos_eliminados pe
SET turno_id = t.id
FROM public.turnos t
WHERE pe.turno_id IS NULL
  AND pe.local_id = t.local_id
  AND pe.eliminado_en >= t.created_at
  AND pe.eliminado_en < COALESCE(
      (SELECT MIN(t2.created_at) FROM public.turnos t2 WHERE t2.local_id = t.local_id AND t2.created_at > t.created_at),
      'infinity'::timestamptz
  );

-- NOTA sobre ventas.usuario_id: esto NO se puede rellenar retroactivamente
-- para ventas viejas — el dato de quién la hizo nunca se guardó, así que no
-- hay de dónde recuperarlo. A partir de esta migración (PASO 3) sí queda
-- guardado en cada venta nueva. Las ventas históricas seguirán mostrando
-- "sin vendedor asignado" en reportes que agrupen por usuario_id — es un
-- dato perdido, no un bug pendiente.
