# Cambios a hacer en Supabase (SQL Editor)

La app (Kotlin) ya quedó lista para usar esto. Lo que falta vive del lado del
servidor: no vino en el zip que me pasaste, así que no lo pude tocar yo.
Estos son los pasos, en orden. Los nombres de columnas son los que se
infieren del código Kotlin (`usuario_id`, `local_id`, `apertura`, `cierre`,
`diferencia`, `created_at`) — si tu tabla real usa otros nombres, ajústalos
antes de ejecutar.

---

## 1. `cerrar_turno`: que cierre en cascada a TODOS los vendedores del local

Hoy `cerrar_turno(p_android_id, p_local_id, p_turno_id, p_cierre)` solo cierra
el turno que le pasas. Necesita, además:

1. Verificar que quien llama es **admin** de ese `local_id` (que ya lo
   verifiques por RLS o adentro de la función — pero verifícalo, hoy la app
   no se lo impedía a nadie).
2. Cerrar el turno del admin con el `p_cierre` que contó (igual que ahora).
3. Cerrar también **todos los demás turnos abiertos de ese `local_id`**
   (los de sus vendedores), sin pedirles un monto contado — como ya
   recogiste el dinero de todos, no hace falta que cada uno cuente el suyo.
4. Cada turno cerrado calcula su propia `diferencia` igual que ya lo hace
   `cerrar_turno` para uno solo.

Ejemplo orientativo (ajusta a tu esquema real):

```sql
create or replace function cerrar_turno(
  p_android_id text,
  p_local_id bigint,
  p_turno_id bigint,
  p_cierre numeric
) returns void
language plpgsql
security definer
as $$
declare
  v_usuario_id bigint;
  v_rol text;
  v_turno record;
begin
  -- 1) quién es y si es admin de este local
  select id, rol into v_usuario_id, v_rol
  from usuarios
  where android_id = p_android_id and local_id = p_local_id;

  if v_rol is distinct from 'admin' then
    raise exception 'Solo un admin puede cerrar el turno de todo el local';
  end if;

  -- 2) cerrar el turno del admin con el monto que contó
  update turnos
  set cierre = p_cierre,
      diferencia = p_cierre - (apertura + coalesce((
        select sum(efectivo) from ventas
        where usuario_id = v_usuario_id and created_at >= turnos.created_at
      ), 0))
  where id = p_turno_id and local_id = p_local_id;

  -- 3) cerrar en cascada los turnos abiertos de los demás vendedores
  for v_turno in
    select * from turnos
    where local_id = p_local_id and cierre is null and id <> p_turno_id
  loop
    update turnos
    set cierre = v_turno.apertura + coalesce((
          select sum(efectivo) from ventas
          where usuario_id = v_turno.usuario_id and created_at >= v_turno.created_at
        ), 0),
        diferencia = 0
    where id = v_turno.id;
  end loop;
end;
$$;
```

> Nota: en el próximo paso de cada vendedor (cuando vuelva a vender), tu
> lógica actual de "el turno se abre solo con la primera acción del día"
> (mencionada en `TurnoRepository.kt`) debe seguir funcionando igual — no
> hay que tocar eso, solo el cierre en cascada de arriba.

## 2. Nuevo RPC `get_turnos_dia`: lista de turnos de un día, para el admin

Se usa cuando el admin elige una fecha pasada en el calendario, para que
pueda marcar con ✓ qué turno(s) quiere ver.

```sql
create or replace function get_turnos_dia(
  p_android_id text,
  p_local_id bigint,
  p_fecha date
) returns table (
  id bigint,
  apertura numeric,
  cierre numeric,
  diferencia numeric,
  created_at timestamptz,
  usuario_nombre text,
  usuario_rol text
)
language plpgsql
security definer
as $$
declare
  v_rol text;
begin
  select rol into v_rol from usuarios
  where android_id = p_android_id and local_id = p_local_id;

  if v_rol is distinct from 'admin' then
    raise exception 'Solo el admin puede ver los turnos de otros días';
  end if;

  return query
  select t.id, t.apertura, t.cierre, t.diferencia, t.created_at,
         u.nombre, u.rol
  from turnos t
  join usuarios u on u.id = t.usuario_id
  where t.local_id = p_local_id
    and t.created_at::date = p_fecha
  order by t.created_at;
end;
$$;
```

La app llama a esto desde `InventarioRepository.getTurnosDelDia(...)`, ya
integrado con la UI de palomitas ✓ en Inventario.

## 3. `get_inventario_dia`: aceptar `p_turno_ids` opcional para filtrar

Cuando el admin marca uno o varios turnos con ✓, la app vuelve a pedir el
día pero mandando `p_turno_ids` (un array de IDs). Si no manda nada, se
comporta exactamente igual que hoy (día completo).

```sql
create or replace function get_inventario_dia(
  p_android_id text,
  p_local_id bigint,
  p_fecha date,
  p_turno_ids bigint[] default null
) returns json
language plpgsql
security definer
as $$
begin
  -- Dentro de tu función actual, donde arma las ventas/mermas/etc del día,
  -- agrega un filtro adicional:
  --   and (p_turno_ids is null or ventas.turno_id = any(p_turno_ids))
  -- Esto requiere que la tabla `ventas` (y las demás que arma el reporte:
  -- mermas, devoluciones, productos nuevos/modificados) tengan una columna
  -- `turno_id` para poder filtrarlas por turno. Si hoy no la tienen, hay
  -- que agregarla y poblarla al momento de crear cada registro.
  ...
end;
$$;
```

> Si `ventas` (u otras tablas del reporte) no tienen `turno_id` todavía, ese
> es el paso previo real: sin esa columna no hay cómo saber qué vendió cada
> turno específico. Avísame cuando tengas el esquema a mano y te ayudo a
> escribir el filtro exacto.

## 4. Seguridad

- Las funciones de arriba usan `security definer` + chequeo de rol adentro,
  que es lo mínimo. Si ya usas RLS (Row Level Security) en `turnos` y
  `ventas`, revisa que esas políticas no bloqueen el `update`/`select` que
  hacen estas funciones al correr como el "dueño" de la función.
- El PIN que ahora pide la app antes de cerrar turno es una segunda
  confirmación en el cliente — la única protección real es el chequeo de
  `rol = 'admin'` dentro de la función en Supabase, porque el cliente
  siempre se puede saltar. No lo quites aunque el PIN ya esté en la app.

---

Cuando tengas el SQL real de tu proyecto (o el archivo de migraciones),
pásamelo y ajusto estas tres funciones a tus nombres exactos de tablas y
columnas en vez de este ejemplo orientativo.
