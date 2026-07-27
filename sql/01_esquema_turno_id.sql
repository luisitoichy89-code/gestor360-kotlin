-- ============================================================================
-- PASO 1 de 3 — ESQUEMA: turno_id como columna real
--
-- Hasta ahora, "¿a qué turno pertenece esta venta/merma/devolución?" se
-- averiguaba comparando su created_at contra el rango de horas del turno
-- (created_at del turno hasta el created_at del turno siguiente). Eso tiene
-- huecos reales en producción: desfase de reloj entre el celular y el
-- servidor, carreras si dos ventas llegan justo en el instante del cierre,
-- y consultas más lentas (joins de rango en vez de comparar un id).
--
-- A partir de ahora, cada fila se marca con su turno_id EN EL MOMENTO EN QUE
-- SE CREA (en el propio INSERT), no se infiere después. Es una sola vez que
-- se decide, nunca más se recalcula ni se puede confundir.
--
-- mermas y devoluciones llevan DOS columnas porque tienen dos momentos que
-- importan: cuándo se pidieron (turno_id) y cuándo se resolvieron
-- (turno_id_resuelto) — un vendedor puede pedir una merma en un turno y el
-- admin resolverla ya en el turno siguiente; get_inventario_dia necesita
-- poder mostrarla en cualquiera de los dos reportes, igual que hace hoy.
-- ============================================================================

ALTER TABLE public.ventas               ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
ALTER TABLE public.mermas                ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
ALTER TABLE public.mermas                ADD COLUMN IF NOT EXISTS turno_id_resuelto bigint REFERENCES public.turnos(id);
ALTER TABLE public.devoluciones          ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);
ALTER TABLE public.devoluciones          ADD COLUMN IF NOT EXISTS turno_id_resuelto bigint REFERENCES public.turnos(id);
ALTER TABLE public.productos_eliminados  ADD COLUMN IF NOT EXISTS turno_id bigint REFERENCES public.turnos(id);

CREATE INDEX IF NOT EXISTS idx_ventas_turno_id                ON public.ventas(turno_id);
CREATE INDEX IF NOT EXISTS idx_mermas_turno_id                ON public.mermas(turno_id);
CREATE INDEX IF NOT EXISTS idx_mermas_turno_id_resuelto        ON public.mermas(turno_id_resuelto);
CREATE INDEX IF NOT EXISTS idx_devoluciones_turno_id           ON public.devoluciones(turno_id);
CREATE INDEX IF NOT EXISTS idx_devoluciones_turno_id_resuelto  ON public.devoluciones(turno_id_resuelto);
CREATE INDEX IF NOT EXISTS idx_productos_eliminados_turno_id   ON public.productos_eliminados(turno_id);

-- Además: ventas.usuario_id existe en la tabla (get_inventario_dia ya hace
-- join contra usuarios por esa columna) pero registrar_venta nunca lo
-- llenaba — todas las ventas quedaban con usuario_id NULL. Es la causa de
-- que "Mis Ventas" no pudiera distinguir vendedores. Se corrige en el
-- PASO 3 (funciones). No hace falta ALTER acá porque la columna ya existe.
