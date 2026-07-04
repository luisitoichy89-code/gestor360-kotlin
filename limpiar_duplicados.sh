#!/data/data/com.termux/files/usr/bin/bash
set -e

BASE="app/src/main/java/org/luisito/gestor360"

if [ ! -d "$BASE" ]; then
  echo "No encuentro $BASE"
  exit 1
fi

declare -a MAPA=(
  "CartItem.kt:data/models"
  "MermaPendiente.kt:data/models"
  "Product.kt:data/models"
  "Sale.kt:data/models"
  "Tarjeta.kt:data/models"
  "User.kt:data/models"
  "DeviceVerificationRepository.kt:data/repository"
  "MermaRepository.kt:data/repository"
  "ProductRepository.kt:data/repository"
  "SaleRepository.kt:data/repository"
  "TarjetaRepository.kt:data/repository"
  "SupabaseClient.kt:data"
  "AccesoViewModel.kt:ui/viewmodels"
  "MermaViewModel.kt:ui/viewmodels"
  "ProductViewModel.kt:ui/viewmodels"
  "SaleViewModel.kt:ui/viewmodels"
  "TarjetaViewModel.kt:ui/viewmodels"
  "AprobacionesScreen.kt:ui/screens"
  "PinLoginScreen.kt:ui/screens"
  "ProductosScreen.kt:ui/screens"
  "TarjetasScreen.kt:ui/screens"
  "VentasScreen.kt:ui/screens"
  "VerificarDispositivoScreen.kt:ui/screens"
  "CommonUi.kt:ui/components"
  "SessionManager.kt:utils"
)

echo "=== Eliminando duplicados ==="
for par in "${MAPA[@]}"; do
  archivo="${par%%:*}"
  carpeta_correcta="${par##*:}"
  ruta_correcta="$BASE/$carpeta_correcta/$archivo"

  encontrados=$(find "$BASE" -type f -name "$archivo")

  if [ -z "$encontrados" ]; then
    echo "  (no se encontró $archivo)"
    continue
  fi

  if [ ! -f "$ruta_correcta" ]; then
    primera=$(echo "$encontrados" | head -n1)
    mkdir -p "$BASE/$carpeta_correcta"
    echo "  -> moviendo $primera a $carpeta_correcta"
    mv "$primera" "$ruta_correcta"
    encontrados=$(find "$BASE" -type f -name "$archivo")
  fi

  while IFS= read -r ruta; do
    if [ "$ruta" != "$ruta_correcta" ]; then
      echo "  eliminando duplicado: $ruta"
      rm "$ruta"
    fi
  done <<< "$encontrados"
done

echo ""
echo "=== Verificación ==="
for par in "${MAPA[@]}"; do
  archivo="${par%%:*}"
  count=$(find "$BASE" -type f -name "$archivo" | wc -l)
  echo "  $archivo: $count copia(s)"
done
