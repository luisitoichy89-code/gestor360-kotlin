# Auditoría de sincronización offline — qué se corrigió

## 1. Batching real (lo que pediste)
`MAX_ACCIONES_POR_CICLO = 50` ya existía, pero se aplicaba con `.take(50)` DESPUÉS de
cargar toda la cola pendiente en memoria (`obtenerPendientes()` no tenía `LIMIT`).
Ahora:
- `AccionPendienteDao.obtenerLotePendiente(50)` trae solo 50 filas de la base (con
  `LIMIT` real en el SQL).
- `SyncManager.sincronizar()` corre un loop que procesa lote tras lote (50 en 50),
  con una pausa de 300ms entre lotes para no ráfaguear Supabase, hasta:
  - que ya no queden acciones pendientes, o
  - que se corte la conexión a mitad de la puesta al día, o
  - llegar a `MAX_ACCIONES_POR_SESION = 500` (tope de seguridad por invocación,
    para no chocar con el límite de ejecución que Android le da a un
    CoroutineWorker en background — si hay más de 500, el resto se procesa en
    el siguiente ciclo periódico o "sincronizar ahora").

Con esto, un dispositivo que estuvo 2 meses offline con 3000 ventas en cola se pone
al día en varias tandas de 500 (en vez de 50 cada 15 minutos, que hubiera tardado
más de 15 horas solo en el "primer pase").

## 2. Bug real: ventas_cache crecía para siempre
Nada marcaba una venta local como `sincronizada = true` después de que
`registrar_venta` tenía éxito en Supabase, así que `limpiarSincronizadas()`
(que ya existía) nunca encontraba nada que borrar. Se agregó:
- `VentaDao.marcarSincronizada(id)` — UPDATE puntual por id.
- Se llama desde `SyncManager` justo cuando el RPC `registrar_venta` confirma éxito.

Ahora la tabla local sí se poda con el tiempo, en vez de crecer sin límite por
meses/años de uso.

## 3. Circuit-breaker para acciones rotas, CON una excepción a propósito
Si una acción falla siempre (ej. referencia a un producto ya eliminado, payload
viejo/incompatible), antes reintentaba para siempre, ocupando un lugar en cada
lote de 50. Ahora, tras `MAX_INTENTOS = 8` fallos, pasa a `estado = "error_permanente"`
y deja de reintentarse sola.

**Excepción deliberada:** `registrar_venta` y `anular_venta` NUNCA se abandonan
automáticamente (`TIPOS_NUNCA_ABANDONAR`), sin importar cuántas veces fallen.
Es dinero — preferible que sigan reintentando para siempre a que una venta real
se pierda silenciosamente por un circuit-breaker.

**Pendiente si quieres cerrarlo del todo:** agregué `obtenerConErrorPermanente()`
en el DAO para poder listarlas, pero no las conecté a ninguna pantalla porque no
tengo `ConflictoEntity.kt` (ya la usan para "stock_negativo") ni una pantalla de
"conflictos". Si me pasas esos dos archivos, las cuelgo ahí para que el admin las
vea y decida (reintentar a mano / descartar) en vez de que solo queden en la base
sin que nadie se entere.

## Riesgo de fondo que ningún límite de lote arregla
Mientras una acción esté offline, existe en un solo lugar: ese teléfono. Si se
pierde el dispositivo, se resetea, o se borran datos de la app antes de
sincronizar, esa acción (venta, merma, etc.) se pierde para siempre — ni el
batching ni el circuit-breaker cambian eso. Ayuda tenerlo en cuenta operativamente
(ej. respaldos periódicos del teléfono, o sincronizar apenas hay señal en vez de
esperar mucho tiempo offline por costumbre).
