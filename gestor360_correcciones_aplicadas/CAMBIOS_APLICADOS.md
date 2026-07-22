# Cambios aplicados — Gestor360°

Basado en `AUDITORIA_ventas_refrescar.md` (21 jul 2026) + `get_ventas` confirmada en vivo.
3 archivos tocados. Sin SQL, sin RPCs nuevas, sin cambios de DAO/Entity.

## Problema 1 — "Ventas realizadas" no reconciliaba online

**Archivo:** `MisVentasScreen.kt`

Se agregó `SaleRepository` + `NetworkMonitor`. En el `LaunchedEffect` que dispara
tanto la carga inicial como el botón refrescar: si hay internet, se llama
`saleRepository.refrescarDesdeServidor(androidId)` **antes** de `cargarDesdeRoom()`.
Esa función ya existía y ya reemplazaba `ventas_cache` de forma atómica con lo
que devuelve `get_ventas` — no se tocó `SaleRepository.kt` ni `VentaDao.kt`. Si
falla o no hay internet, el comportamiento es exactamente el de antes
(`cargarDesdeRoom()` con lo que ya haya).

Se confirmó con la `get_ventas` en vivo que compartiste (`SELECT * FROM
ventas WHERE local_id = ...`) que sí devuelve `usuario_id` por fila, así que
el filtrado por vendedor que ya hace `cargarDesdeRoom()` sigue funcionando
igual con datos frescos del servidor.

## Problema 2 — Refrescar en Inventario se vaciaba offline

**Archivos:** `InventarioViewModel.kt`, `InventarioRepository.kt`

- **Causa raíz A** (`turnosSeleccionadosIds` quedaba pegado tras cerrar turno):
  `cerrarTurno()` ya no fija ese campo. Es exclusivo para navegar turnos de
  días pasados (así lo gatea `InventarioScreen` con `!uiState.esHoy`), y
  `cerrarTurno()` solo se puede invocar con la fecha activa en hoy.

- **Causa raíz C** (el botón no forzaba nada): `refrescar()` ahora llama
  `cargarFecha(..., forzarRefresh = true)`, hilado real hasta
  `getInventarioDia()`. Con internet, se espera la respuesta del servidor
  (`isLoading` queda visible mientras tanto) en vez de devolver la caché de
  inmediato y refrescar en un coroutine suelto.

- **Causa raíz B** (no sumaba ventas pendientes) + la rama `turnoIds` sin
  conexión: `getInventarioDia()`, con `forzarRefresh = true` y sin internet
  (haya venido con `turnoIds` explícito o no), en vez de fallar o
  reconstruir todo desde cero con `construirDesdeRoom()`, usa el nuevo
  `resolverOfflineForzado()`: parte de la última caché confiable y le suma
  (`fusionarConVentasPendientes()`) las ventas de este dispositivo con
  `sincronizada = false` de esa fecha — con chequeo de duplicados por id,
  porque una venta puede estar ya confirmada en la caché del servidor
  mientras localmente sigue sin marcarse sincronizada. Reutiliza
  `toVentaInfo()` y `fusionarProductosVendidos()`, que ya existían.

Todo lo demás (`toggleTurnoSeleccionado`, `seleccionarTodosLosTurnos`,
`seleccionarFecha`, la carga inicial, y la rama `turnoIds` sin forzar refresh)
queda con el comportamiento exacto de antes.

## Pendiente / a tu criterio (no bloqueante)

`get_ventas` no filtra por fecha ni turno — trae todo el historial del local.
La auditoría ya lo señalaba como algo a confirmar antes de enganchar esto a
una pantalla de apertura frecuente; ahora "Mis Ventas" la llama en cada
apertura y cada refresco. No es un bug nuevo y no se tocó nada del lado
servidor, pero si el local tiene mucho volumen de ventas históricas vale la
pena migrarla a un filtro por fecha/turno más adelante.
