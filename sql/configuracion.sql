-- RPC para leer configuracion (tabla global, no depende de cliente_id, así
-- que no necesita el patrón "resolver por android_id" del resto de funciones).
CREATE OR REPLACE FUNCTION public.obtener_configuracion()
RETURNS SETOF configuracion
LANGUAGE sql
SECURITY DEFINER
AS $function$
SELECT * FROM configuracion;
$function$;

GRANT EXECUTE ON FUNCTION public.obtener_configuracion() TO anon, authenticated;

-- Filas iniciales (ajusta la URL a tu repo real). No asume que "clave" tenga
-- una constraint UNIQUE, así que evita duplicados con NOT EXISTS en vez de
-- ON CONFLICT.
INSERT INTO configuracion (clave, valor)
SELECT 'version_actual', '1.0.0'
WHERE NOT EXISTS (SELECT 1 FROM configuracion WHERE clave = 'version_actual');

INSERT INTO configuracion (clave, valor)
SELECT 'url_descarga', 'https://github.com/TU_USUARIO/TU_REPO/releases/latest'
WHERE NOT EXISTS (SELECT 1 FROM configuracion WHERE clave = 'url_descarga');

-- Para publicar una actualización después, solo necesitas:
-- UPDATE configuracion SET valor = '1.1.0' WHERE clave = 'version_actual';
