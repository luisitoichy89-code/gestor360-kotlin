# Fase 1 — Inventario con turno_id explícito

## Qué se hizo

### Supabase (`sql/migracion_turno_id.sql`)
- `ALTER TABLE` + índice para `turno_id` en `ventas`, `mermas`, `devoluciones`,
  `productos_eliminados` (sin backfill, como pediste).
- Función helper `obtener_o_crear_turno_abierto(local_id, usuario_id, cliente_id)`:
  usa `SELECT ... FOR UPDATE` para evitar que dos ventas casi simultáneas creen
  dos turnos abiertos para el mismo local. Si no hay turno abierto, lo crea.
- `registrar_venta`, `crear_merma`, `solicitar_devolucion` reescritas para usar
  el helper y grabar `turno_id` en el INSERT correspondiente.

### Android
- `VentaEntity`, `MermaEntity`, `ProductoEliminadoCacheEntity`: campo nuevo
  `turnoId: Long? = null`.
- `Sale` y `Devolucion` (modelos de dominio): campo nuevo `turno_id: Long? = null`
  — `Devolucion` se guarda como JSON completo en `DevolucionCacheEntity`, así que
  ahí no hace falta tocar el esquema de Room, solo el modelo.
- `AppDatabase.kt`: `MIGRATION_14_15` (`ALTER TABLE ADD COLUMN turnoId` en
  `ventas_cache`, `mermas_cache`, `productos_eliminados_cache`), versión 14→15.
- `InventarioRepository.kt`: la reconstrucción offline (`construirDesdeRoom`)
  ahora filtra por `turno_id` cuando hay turno activo cacheado y es el día de
  hoy, en vez de comparar strings de fecha. Si el usuario seleccionó turnos
  explícitamente (`turnoIds`) se respeta esa selección. Los registros sin
  `turno_id` (de antes de la migración) se siguen incluyendo por fecha como
  antes, para no hacerlos desaparecer. La ruta "online" (`get_inventario_dia`)
  sigue igual porque no tengo su definición actual — ver pendientes abajo.

### `get_inventario_dia` — ya cerrada, sin cambios
La versión que mandaste ya estaba escrita esperando `turno_id` (`v.turno_id`,
`m.turno_id`, `d.turno_id`, `pe.turno_id` en los `WHERE`), incluyendo el
fallback del punto 3: si `p_turno_ids` es NULL y la fecha es HOY, usa el
turno abierto actual; si es un día pasado, no filtra por turno. Con el
`ALTER TABLE` de la migración corriendo, esta función queda funcional tal
cual está — no hizo falta tocarla.

## Pendiente — ¡nada! Fase 1 quedó completa

`eliminar_producto` solo borra el producto; el trigger existente
`trg_productos_eliminados` → `fn_registrar_producto_eliminado()` ya inserta
en `productos_eliminados` con `turno_id`, llamando a una función
`obtener_turno_abierto()` que **todavía no existe** en tu base (por eso el
trigger la llama dentro de un `BEGIN/EXCEPTION WHEN OTHERS` — para no
romper el borrado si la función faltaba, grabando `turno_id = NULL` en
silencio mientras tanto).

`sql/migracion_turno_id.sql` ahora crea esa función
(`obtener_turno_abierto`, de solo lectura — a propósito **no** crea un
turno nuevo si no hay uno abierto, porque borrar un producto no debería
disparar la apertura de un turno). Con eso el trigger empieza a funcionar
tal cual está, sin tocar ni el trigger ni `eliminar_producto`.

Corre el `.sql` completo, de arriba a abajo, en el SQL editor de Supabase.
No hace falta que me mandes nada más para esta fase.

## Cosas que noté de paso, sin tocar (avísame si quieres que las agarre)

- El advisor de Supabase marca RLS deshabilitado en `ventas` y `tarjetas`
  (CRITICAL). No lo toqué porque no era parte de este pedido y activar RLS
  sin las policies correctas puede tumbar la app.
- `MermaDao`, `TurnoDao`, `UserDao`, `LocalDao`, `TarjetaDao`,
  `AccionPendienteDao`, `ConflictoDao`, `AprobacionStockCacheDao` no vinieron
  en ninguno de los 3 zips, así que no los toqué. No hacía falta para que
  compile (los campos nuevos son columnas Room normales), pero si quieres
  queries que filtren directo por `turnoId` en SQL de Room (en vez del
  filtrado en memoria que hice en `InventarioRepository`), mándame esos DAOs.
- `UserDao.kt`/`UserEntity.kt` siguen pendientes de mover a su subpaquete
  correcto (lo tenías anotado en tu nota de la sesión anterior) — no lo tocué
  porque no vino en estos zips y no es parte de este pedido.
