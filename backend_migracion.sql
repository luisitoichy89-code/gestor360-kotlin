-- =====================================================================
-- MIGRACIÓN: tarjeta en la venta + reporte completo de dinero y stock +
-- registro total de quién hizo qué (admin se autoaprueba al instante,
-- vendedor queda pendiente hasta que un admin resuelve). Corre esto
-- completo en el SQL Editor de Supabase.
--
-- Recrea crear_producto, actualizar_producto, solicitar_producto,
-- solicitar_aumento_stock, crear_merma, resolver_aprobacion,
-- resolver_merma, get_aprobaciones, get_mermas_pendientes,
-- solicitar_anular_venta y anular_venta con los mismos nombres y
-- parámetros que ya usa tu app (confirmados en ProductRepository.kt,
-- MermaRepository.kt y AprobacionStockRepository.kt), así que no debería
-- romper nada del lado del cliente. Agrega registrar_merma_admin, que sí
-- es nueva — la usa el botón de merma del admin.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) VENTAS: guardar qué tarjeta se usó para cobrar
-- ---------------------------------------------------------------------
alter table public.ventas
  add column if not exists tarjeta_id bigint references public.tarjetas(id);

alter table public.ventas
  add column if not exists anulada boolean not null default false;

-- ---------------------------------------------------------------------
-- 2) registrar_venta: ahora recibe la tarjeta usada (opcional)
-- ---------------------------------------------------------------------
create or replace function public.registrar_venta(
  p_android_id text, p_local_id bigint, p_producto_id bigint,
  p_cantidad numeric, p_total numeric, p_metodo text,
  p_efectivo numeric, p_transferencia numeric,
  p_cliente_ci text default null, p_cliente_tel text default null, p_cliente_nombre text default null,
  p_tarjeta_id bigint default null
) returns uuid
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_usuario_id bigint;
  v_venta_id uuid;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then
    raise exception 'Usuario no encontrado para android_id %', p_android_id;
  end if;

  update productos set stock = greatest(stock - p_cantidad, 0)
  where id = p_producto_id and local_id = p_local_id;

  insert into ventas (
    producto_id, cantidad, total, metodo, efectivo, transferencia,
    usuario_id, local_id, cliente_ci, cliente_tel, cliente_nombre, tarjeta_id, anulada, created_at
  ) values (
    p_producto_id, p_cantidad, p_total, p_metodo, p_efectivo, p_transferencia,
    v_usuario_id, p_local_id, p_cliente_ci, p_cliente_tel, p_cliente_nombre, p_tarjeta_id, false, now()
  ) returning id into v_venta_id;

  return v_venta_id;
end;
$function$;

-- ---------------------------------------------------------------------
-- 3) TODO queda registrado, sea quien sea quien lo haga. Admin: se
--    aplica y se auto-aprueba en el mismo momento (resuelto_por = él
--    mismo). Vendedor: queda "pendiente" hasta que un admin lo resuelve.
--    Así en un local con varios usuarios siempre se sabe quién pidió
--    qué y quién lo aprobó.
-- ---------------------------------------------------------------------
create table if not exists public.aprobaciones (
  id bigserial primary key,
  producto_id bigint references public.productos(id),
  producto_nombre text not null default '',
  precio numeric,
  cantidad numeric not null default 0,
  tipo text not null check (tipo in ('producto','aumento','anular_venta')),
  estado text not null default 'pendiente' check (estado in ('pendiente','aprobado','rechazado')),
  venta_id uuid,
  venta_total numeric,
  local_id bigint not null,
  solicitado_por bigint references public.usuarios(id),
  resuelto_por bigint references public.usuarios(id),
  created_at timestamptz not null default now(),
  resuelto_at timestamptz
);
alter table public.aprobaciones add column if not exists resuelto_por bigint references public.usuarios(id);
alter table public.aprobaciones add column if not exists resuelto_at timestamptz;
alter table public.aprobaciones add column if not exists created_at timestamptz not null default now();

create table if not exists public.mermas (
  id bigserial primary key,
  producto_id bigint not null references public.productos(id),
  producto_nombre text not null default '',
  cantidad numeric not null,
  motivo text,
  estado text not null default 'pendiente' check (estado in ('pendiente','aprobada','rechazada')),
  local_id bigint not null,
  solicitado_por bigint references public.usuarios(id),
  resuelto_por bigint references public.usuarios(id),
  created_at timestamptz not null default now(),
  resuelto_at timestamptz
);
alter table public.mermas add column if not exists resuelto_por bigint references public.usuarios(id);
alter table public.mermas add column if not exists resuelto_at timestamptz;
alter table public.mermas add column if not exists created_at timestamptz not null default now();

create index if not exists idx_aprobaciones_local_estado on public.aprobaciones (local_id, estado);
create index if not exists idx_mermas_local_estado on public.mermas (local_id, estado);

-- crear_producto: admin, se aplica y se registra aprobado al instante.
create or replace function public.crear_producto(
  p_android_id text, p_local_id bigint, p_nombre text, p_precio numeric,
  p_stock numeric, p_ubicacion text, p_categoria text
) returns bigint
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_usuario_id bigint; v_producto_id bigint;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  insert into productos (nombre, precio, stock, ubicacion, categoria, local_id, created_at, updated_at)
  values (p_nombre, p_precio, p_stock, p_ubicacion, p_categoria, p_local_id, now(), now())
  returning id into v_producto_id;

  insert into aprobaciones (producto_id, producto_nombre, precio, cantidad, tipo, estado, local_id, solicitado_por, resuelto_por, resuelto_at)
  values (v_producto_id, p_nombre, p_precio, p_stock, 'producto', 'aprobado', p_local_id, v_usuario_id, v_usuario_id, now());

  return v_producto_id;
end;
$function$;

-- actualizar_producto: admin, se aplica al instante. Si cambia el stock,
-- queda registrado como agregado (aprobaciones) o merma (mermas), aprobado.
create or replace function public.actualizar_producto(
  p_android_id text, p_local_id bigint, p_id bigint, p_nombre text, p_precio numeric,
  p_stock numeric, p_ubicacion text, p_categoria text
) returns void
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_usuario_id bigint; v_stock_actual numeric; v_diff numeric;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  select stock into v_stock_actual from productos where id = p_id and local_id = p_local_id;
  if v_stock_actual is null then raise exception 'Producto no encontrado'; end if;

  update productos set nombre = p_nombre, precio = p_precio, stock = p_stock,
    ubicacion = p_ubicacion, categoria = p_categoria, updated_at = now()
  where id = p_id and local_id = p_local_id;

  v_diff := p_stock - v_stock_actual;
  if v_diff > 0 then
    insert into aprobaciones (producto_id, producto_nombre, cantidad, tipo, estado, local_id, solicitado_por, resuelto_por, resuelto_at)
    values (p_id, p_nombre, v_diff, 'aumento', 'aprobado', p_local_id, v_usuario_id, v_usuario_id, now());
  elsif v_diff < 0 then
    insert into mermas (producto_id, producto_nombre, cantidad, motivo, estado, local_id, solicitado_por, resuelto_por, resuelto_at)
    values (p_id, p_nombre, abs(v_diff), 'Ajuste desde edición de producto', 'aprobada', p_local_id, v_usuario_id, v_usuario_id, now());
  end if;
end;
$function$;

-- registrar_merma_admin: botón dedicado de merma para admin (con motivo real,
-- no un ajuste genérico). Se aplica y se aprueba al instante.
create or replace function public.registrar_merma_admin(
  p_android_id text, p_local_id bigint, p_producto_id bigint, p_cantidad numeric, p_motivo text
) returns bigint
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_usuario_id bigint; v_nombre text; v_merma_id bigint;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  select nombre into v_nombre from productos where id = p_producto_id and local_id = p_local_id;
  if v_nombre is null then raise exception 'Producto no encontrado'; end if;

  update productos set stock = greatest(stock - p_cantidad, 0), updated_at = now()
  where id = p_producto_id and local_id = p_local_id;

  insert into mermas (producto_id, producto_nombre, cantidad, motivo, estado, local_id, solicitado_por, resuelto_por, resuelto_at)
  values (p_producto_id, v_nombre, p_cantidad, p_motivo, 'aprobada', p_local_id, v_usuario_id, v_usuario_id, now())
  returning id into v_merma_id;

  return v_merma_id;
end;
$function$;

-- solicitar_producto: vendedor propone un producto nuevo. Queda pendiente,
-- todavía no existe en "productos".
create or replace function public.solicitar_producto(
  p_android_id text, p_local_id bigint, p_nombre text, p_precio numeric, p_cantidad numeric
) returns bigint
language plpgsql security definer set search_path to 'public'
as $function$
declare v_usuario_id bigint; v_id bigint;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  insert into aprobaciones (producto_nombre, precio, cantidad, tipo, estado, local_id, solicitado_por)
  values (p_nombre, p_precio, p_cantidad, 'producto', 'pendiente', p_local_id, v_usuario_id)
  returning id into v_id;
  return v_id;
end;
$function$;

-- solicitar_aumento_stock: vendedor pide agregar stock a un producto existente.
create or replace function public.solicitar_aumento_stock(
  p_android_id text, p_local_id bigint, p_producto_id bigint, p_cantidad numeric
) returns bigint
language plpgsql security definer set search_path to 'public'
as $function$
declare v_usuario_id bigint; v_nombre text; v_id bigint;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  select nombre into v_nombre from productos where id = p_producto_id and local_id = p_local_id;
  if v_nombre is null then raise exception 'Producto no encontrado'; end if;

  insert into aprobaciones (producto_id, producto_nombre, cantidad, tipo, estado, local_id, solicitado_por)
  values (p_producto_id, v_nombre, p_cantidad, 'aumento', 'pendiente', p_local_id, v_usuario_id)
  returning id into v_id;
  return v_id;
end;
$function$;

-- crear_merma: vendedor pide registrar una merma. Queda pendiente.
create or replace function public.crear_merma(
  p_android_id text, p_local_id bigint, p_producto_id bigint, p_cantidad numeric, p_motivo text
) returns bigint
language plpgsql security definer set search_path to 'public'
as $function$
declare v_usuario_id bigint; v_nombre text; v_id bigint;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  select nombre into v_nombre from productos where id = p_producto_id and local_id = p_local_id;
  if v_nombre is null then raise exception 'Producto no encontrado'; end if;

  insert into mermas (producto_id, producto_nombre, cantidad, motivo, estado, local_id, solicitado_por)
  values (p_producto_id, v_nombre, p_cantidad, p_motivo, 'pendiente', p_local_id, v_usuario_id)
  returning id into v_id;
  return v_id;
end;
$function$;

-- get_aprobaciones / get_mermas_pendientes: solo lo pendiente (para la
-- pantalla de Aprobaciones del admin).
create or replace function public.get_aprobaciones(p_android_id text, p_local_id bigint)
returns setof public.aprobaciones
language plpgsql security definer set search_path to 'public'
as $function$
declare v_usuario_id bigint;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;
  return query select * from aprobaciones where local_id = p_local_id and estado = 'pendiente' order by created_at;
end;
$function$;

create or replace function public.get_mermas_pendientes(p_android_id text, p_local_id bigint)
returns setof public.mermas
language plpgsql security definer set search_path to 'public'
as $function$
declare v_usuario_id bigint;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;
  return query select * from mermas where local_id = p_local_id and estado = 'pendiente' order by created_at;
end;
$function$;

-- resolver_aprobacion: admin aprueba/rechaza lo que pidió un vendedor.
-- Al aprobar 'producto' lo crea; 'aumento' suma stock; 'anular_venta' anula.
create or replace function public.resolver_aprobacion(
  p_android_id text, p_local_id bigint, p_id bigint, p_estado text, p_aprobado_por bigint
) returns void
language plpgsql security definer set search_path to 'public'
as $function$
declare v_a record; v_producto_id bigint;
begin
  select * into v_a from aprobaciones where id = p_id and local_id = p_local_id and estado = 'pendiente';
  if not found then raise exception 'Aprobación no encontrada o ya resuelta'; end if;

  if p_estado <> 'aprobado' then
    update aprobaciones set estado = p_estado, resuelto_por = p_aprobado_por, resuelto_at = now() where id = p_id;
    return;
  end if;

  if v_a.tipo = 'producto' then
    insert into productos (nombre, precio, stock, local_id, created_at, updated_at)
    values (v_a.producto_nombre, v_a.precio, v_a.cantidad, p_local_id, now(), now())
    returning id into v_producto_id;
    update aprobaciones set estado = 'aprobado', producto_id = v_producto_id, resuelto_por = p_aprobado_por, resuelto_at = now() where id = p_id;
  elsif v_a.tipo = 'aumento' then
    update productos set stock = stock + v_a.cantidad, updated_at = now() where id = v_a.producto_id and local_id = p_local_id;
    update aprobaciones set estado = 'aprobado', resuelto_por = p_aprobado_por, resuelto_at = now() where id = p_id;
  elsif v_a.tipo = 'anular_venta' then
    update ventas set anulada = true where id = v_a.venta_id and local_id = p_local_id;
    update aprobaciones set estado = 'aprobado', resuelto_por = p_aprobado_por, resuelto_at = now() where id = p_id;
  end if;
end;
$function$;

-- resolver_merma: admin aprueba/rechaza una merma pedida por un vendedor.
create or replace function public.resolver_merma(
  p_android_id text, p_local_id bigint, p_merma_id bigint, p_estado text
) returns void
language plpgsql security definer set search_path to 'public'
as $function$
declare v_usuario_id bigint; v_m record;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  select * into v_m from mermas where id = p_merma_id and local_id = p_local_id and estado = 'pendiente';
  if not found then raise exception 'Merma no encontrada o ya resuelta'; end if;

  if p_estado = 'aprobada' then
    update productos set stock = greatest(stock - v_m.cantidad, 0), updated_at = now()
    where id = v_m.producto_id and local_id = p_local_id;
  end if;

  update mermas set estado = p_estado, resuelto_por = v_usuario_id, resuelto_at = now() where id = p_merma_id;
end;
$function$;

-- solicitar_anular_venta: vendedor pide anular una venta (queda pendiente).
create or replace function public.solicitar_anular_venta(
  p_android_id text, p_local_id bigint, p_venta_id uuid, p_venta_total numeric
) returns bigint
language plpgsql security definer set search_path to 'public'
as $function$
declare v_usuario_id bigint; v_id bigint;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  insert into aprobaciones (producto_nombre, tipo, estado, venta_id, venta_total, local_id, solicitado_por)
  values ('Anular venta', 'anular_venta', 'pendiente', p_venta_id, p_venta_total, p_local_id, v_usuario_id)
  returning id into v_id;
  return v_id;
end;
$function$;

-- anular_venta: admin anula directo (sin pasar por aprobación), pero queda
-- igual registrado, auto-aprobado, para saber quién la anuló.
create or replace function public.anular_venta(p_android_id text, p_local_id bigint, p_venta_id uuid)
returns void
language plpgsql security definer set search_path to 'public'
as $function$
declare v_usuario_id bigint; v_total numeric;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  select total into v_total from ventas where id = p_venta_id and local_id = p_local_id;
  update ventas set anulada = true where id = p_venta_id and local_id = p_local_id;

  insert into aprobaciones (producto_nombre, tipo, estado, venta_id, venta_total, local_id, solicitado_por, resuelto_por, resuelto_at)
  values ('Anular venta', 'anular_venta', 'aprobado', p_venta_id, v_total, p_local_id, v_usuario_id, v_usuario_id, now());
end;
$function$;

-- Permitir el tipo 'eliminacion' (borrado de producto) en aprobaciones.
alter table public.aprobaciones drop constraint if exists aprobaciones_tipo_check;
alter table public.aprobaciones add constraint aprobaciones_tipo_check
  check (tipo in ('producto','aumento','anular_venta','eliminacion'));

-- eliminar_producto: admin borra un producto. Se guarda el nombre/stock que
-- tenía ANTES de borrarlo (auto-aprobado), porque una vez borrado ya no hay
-- fila en "productos" a la que referenciar.
create or replace function public.eliminar_producto(p_android_id text, p_local_id bigint, p_id bigint)
returns void
language plpgsql security definer set search_path to 'public'
as $function$
declare v_usuario_id bigint; v_nombre text; v_stock numeric;
begin
  select id into v_usuario_id from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  select nombre, stock into v_nombre, v_stock from productos where id = p_id and local_id = p_local_id;
  if v_nombre is null then raise exception 'Producto no encontrado'; end if;

  delete from productos where id = p_id and local_id = p_local_id;

  insert into aprobaciones (producto_id, producto_nombre, cantidad, tipo, estado, local_id, solicitado_por, resuelto_por, resuelto_at)
  values (null, v_nombre, v_stock, 'eliminacion', 'aprobado', p_local_id, v_usuario_id, v_usuario_id, now());
end;
$function$;

-- ---------------------------------------------------------------------
-- 4) get_inventario_dia: recreada. Ojo con las secciones marcadas
--    "AJUSTAR": ahí necesito que confirmes/mandes los nombres reales de
--    tabla/columna, porque adiviné en base a lo que sí conozco de tu
--    esquema (ventas, productos, tarjetas, usuarios). Todo lo demás
--    (ventas con cliente+tarjeta, productos_vendidos con total_vendido
--    y total_actual, totales de dinero) es 100% seguro porque sale de
--    ventas/productos, que ya conozco completos.
-- ---------------------------------------------------------------------
create or replace function public.get_inventario_dia(p_android_id text, p_local_id bigint, p_fecha date)
returns jsonb
language plpgsql security definer set search_path to 'public'
as $function$
declare
  v_usuario_id bigint; v_rol text;
  v_turno jsonb;
  v_productos_nuevos jsonb;
  v_productos_modificados jsonb;
  v_devueltos jsonb;
  v_ventas jsonb;
  v_productos_vendidos jsonb;
  v_totales jsonb;
  v_solo_lectura boolean;
  v_productos_eliminados jsonb;
begin
  select id, rol into v_usuario_id, v_rol from usuarios where android_id = p_android_id limit 1;
  if v_usuario_id is null then raise exception 'Usuario no encontrado'; end if;

  v_solo_lectura := p_fecha < current_date;

  -- AJUSTAR: turno (nombres de tabla/columna adivinados: turnos, apertura, cierre, usuario_id)
  select jsonb_build_object(
    'id', t.id, 'apertura', t.apertura, 'cierre', t.cierre,
    'diferencia', case when t.cierre is not null then t.cierre - (t.apertura + coalesce(tot.esperado, 0)) end,
    'created_at', t.created_at, 'usuario_nombre', u.nombre, 'usuario_rol', u.rol
  ) into v_turno
  from turnos t
  left join usuarios u on u.id = t.usuario_id
  left join lateral (
    select sum(v.efectivo) esperado from ventas v
    where v.local_id = p_local_id and v.anulada = false and v.created_at::date = p_fecha
  ) tot on true
  where t.local_id = p_local_id and t.created_at::date = p_fecha
  order by t.created_at desc limit 1;

  -- PRODUCTOS NUEVOS: tanto los que crea un admin directo (autoaprobado al
  -- instante) como los que un vendedor propuso Y un admin aprobó. Los que
  -- un vendedor propuso y siguen pendientes o fueron rechazados NO aparecen.
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', coalesce(a.producto_id, a.id), 'nombre', a.producto_nombre, 'precio', a.precio,
    'stock', a.cantidad, 'ubicacion', p.ubicacion, 'fecha', a.resuelto_at,
    'solicitado_por_nombre', us.nombre, 'resuelto_por_nombre', ur.nombre
  ) order by a.producto_nombre), '[]'::jsonb)
  into v_productos_nuevos
  from aprobaciones a
  left join productos p on p.id = a.producto_id
  left join usuarios us on us.id = a.solicitado_por
  left join usuarios ur on ur.id = a.resuelto_por
  where a.local_id = p_local_id and a.tipo = 'producto' and a.estado = 'aprobado'
    and a.resuelto_at::date = p_fecha;

  -- Productos modificados: ediciones directas de admin (nombre/precio/etc.)
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', p.id, 'nombre', p.nombre, 'precio', p.precio, 'stock', p.stock, 'fecha', p.updated_at
  ) order by p.nombre), '[]'::jsonb)
  into v_productos_modificados
  from productos p
  where p.local_id = p_local_id and p.updated_at::date = p_fecha and p.created_at::date <> p_fecha;

  -- Devueltos: se deja igual que antes (tabla existente, no se toca)
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', d.id, 'producto_nombre', p.nombre, 'cantidad', d.cantidad, 'metodo', d.metodo,
    'estado', d.estado, 'solicitado_por_nombre', us.nombre,
    'resuelto_por_nombre', ur.nombre, 'resuelto_por_rol', ur.rol, 'fecha', d.created_at
  ) order by d.created_at), '[]'::jsonb)
  into v_devueltos
  from devoluciones d
  left join productos p on p.id = d.producto_id
  left join usuarios us on us.id = d.solicitado_por
  left join usuarios ur on ur.id = d.resuelto_por
  where d.local_id = p_local_id and d.created_at::date = p_fecha;

  -- Ventas del día, con cliente y tarjeta ya resueltos (esto sí es 100% seguro)
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', v.id, 'producto_nombre', p.nombre, 'cantidad', v.cantidad, 'total', v.total,
    'metodo', v.metodo, 'efectivo', v.efectivo, 'transferencia', v.transferencia,
    'anulada', v.anulada, 'usuario_nombre', u.nombre, 'usuario_rol', u.rol, 'fecha', v.created_at,
    'cliente_ci', v.cliente_ci, 'cliente_tel', v.cliente_tel, 'cliente_nombre', v.cliente_nombre,
    'tarjeta_banco', tj.banco, 'tarjeta_numero', tj.numero, 'tarjeta_titular', tj.titular
  ) order by v.created_at desc), '[]'::jsonb)
  into v_ventas
  from ventas v
  join productos p on p.id = v.producto_id
  left join usuarios u on u.id = v.usuario_id
  left join tarjetas tj on tj.id = v.tarjeta_id
  where v.local_id = p_local_id and v.created_at::date = p_fecha;

  -- Productos vendidos ese día, alfabético. total_agregado sale de
  -- "aprobaciones" (tipo='aumento', aprobado); total_merma sale de "mermas"
  -- (estado='aprobada'). total_inicial = actual + vendido + merma - agregado.
  select coalesce(jsonb_agg(jsonb_build_object(
    'nombre', x.nombre,
    'total_vendido', x.total_vendido,
    'total_actual', x.stock_actual,
    'total_agregado', x.total_agregado,
    'total_merma', x.total_merma,
    'total_inicial', x.stock_actual + x.total_vendido + x.total_merma - x.total_agregado
  ) order by x.nombre), '[]'::jsonb)
  into v_productos_vendidos
  from (
    select p.id, p.nombre, p.stock as stock_actual,
      sum(v.cantidad) as total_vendido,
      coalesce((
        select sum(a.cantidad) from aprobaciones a
        where a.producto_id = p.id and a.tipo = 'aumento' and a.estado = 'aprobado' and a.resuelto_at::date = p_fecha
      ), 0) as total_agregado,
      coalesce((
        select sum(m.cantidad) from mermas m
        where m.producto_id = p.id and m.estado = 'aprobada' and m.resuelto_at::date = p_fecha
      ), 0) as total_merma
    from ventas v
    join productos p on p.id = v.producto_id
    where v.local_id = p_local_id and v.created_at::date = p_fecha and v.anulada = false
    group by p.id, p.nombre, p.stock
  ) x;

  -- Totales de dinero del día (excluye anuladas) — 100% seguro
  select jsonb_build_object(
    'efectivo', coalesce(sum(v.efectivo), 0),
    'transferencia', coalesce(sum(v.transferencia), 0),
    'cantidad_ventas', count(*)
  ) into v_totales
  from ventas v
  where v.local_id = p_local_id and v.created_at::date = p_fecha and v.anulada = false;

  -- Productos eliminados ese día (auditoría: nombre + stock que tenía al borrarse)
  select coalesce(jsonb_agg(jsonb_build_object(
    'id', a.id, 'nombre', a.producto_nombre, 'stock', a.cantidad, 'fecha', a.resuelto_at,
    'resuelto_por_nombre', ur.nombre
  ) order by a.resuelto_at desc), '[]'::jsonb)
  into v_productos_eliminados
  from aprobaciones a
  left join usuarios ur on ur.id = a.resuelto_por
  where a.local_id = p_local_id and a.tipo = 'eliminacion' and a.resuelto_at::date = p_fecha;

  return jsonb_build_object(
    'fecha', p_fecha,
    'solo_lectura', v_solo_lectura,
    'turno', v_turno,
    'productos_nuevos', v_productos_nuevos,
    'productos_modificados', v_productos_modificados,
    'productos_eliminados', v_productos_eliminados,
    'devueltos', v_devueltos,
    'ventas', v_ventas,
    'productos_vendidos', v_productos_vendidos,
    'totales_ventas', v_totales
  );
end;
$function$;
