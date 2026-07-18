# Cambios hechos — auditoría de Inventario

## 1. Bug raíz: "productos vendidos" solo mostraba total_vendido
En `InventarioRepository.kt`, el refresco contra el servidor (`refrescarDesdeServidor`)
corría en un coroutine desechable (`CoroutineScope(Dispatchers.IO).launch { ... }`)
cuyo resultado **nunca se propagaba a la UI**: solo se guardaba en la caché de Room.
La pantalla se quedaba mostrando para siempre la versión local (construida solo con
las ventas pendientes de sincronizar), donde `total_actual`, `total_agregado`,
`total_merma` y `total_inicial` no existen y quedan en 0 — por eso solo se veía
"Total vendidos". Se corrigió agregando un callback (`onActualizadoDesdeServidor`)
que se propaga hasta el ViewModel y actualiza el estado en cuanto el servidor
responde. Archivos: `InventarioRepository.kt`, `InventarioViewModel.kt`.

## 2. Bandeja inicial (fecha / turno) eliminada
Se quitó el bloque "Sin actividad registrada todavía..." y toda la tarjeta de turno
del cuerpo de la pantalla. Ahora lo primero que se ve es **Total efectivo / Total
transferencia / Total generado**.
Para no perder funciones que vivían ahí, se movieron a la barra superior:
- La fecha seleccionada (debajo del título).
- El ícono de "solo lectura" cuando aplica.
- El botón de **cerrar turno** (solo visible si hay turno abierto, es hoy, y no es
  solo lectura). Nota: el detalle de "sobran/faltan $X" que se mostraba tras cerrar
  el turno ya no tiene dónde mostrarse — si lo quieres, dime dónde reubicarlo
  (¿snackbar? ¿dentro del mismo diálogo de cierre?).

## 3. Tarjeta: nombre arriba, número abajo, total nunca se oculta
`TarjetaResumenRow` se rediseñó: el nombre y número de cuenta ahora están en una
columna con `weight` que se recorta con "…" si es muy larga, y el monto total
queda en un `Text` de ancho fijo al final de la fila — así un nombre largo ya no
empuja ni oculta el valor.

## 4. Auditoría del SQL (`get_inventario_dia`)
- **Bug real:** el turno se creaba automáticamente para cualquier fecha que se
  consultara — incluidas fechas pasadas navegadas en modo solo lectura. Con solo
  abrir un día viejo en el calendario, se insertaba un turno fantasma con
  apertura=0. Corregido: el alta automática ahora solo ocurre si `p_fecha` es hoy.
- **Bug real:** si el dispositivo solo tenía licencia (sin usuario logueado), se
  insertaba el turno con `usuario_id = NULL`. Corregido: ya no se crea turno sin
  usuario identificado.
- **Dato mal calculado:** `total_inicial` en "productos vendidos" no contaba las
  devoluciones que vuelven a stock (`estado = 'aprobada_stock'`), así que un
  producto con devolución ese día quedaba descuadrado. Se agregó ese término a
  la fórmula.
- Se agregó `totales_por_tarjeta` al modelo Kotlin (`InventarioDia.kt`) — ya venía
  del SQL pero no existía en el modelo, así que se perdía silenciosamente.

**Este archivo SQL no se aplicó a ninguna base de datos** (no tengo acceso a tu
Supabase) — cópialo al editor SQL de Supabase y ejecútalo cuando quieras aplicar
el fix.
