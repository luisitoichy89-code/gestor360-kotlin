package org.luisito.gestor360.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter

/**
 * Genera un CSV en el almacenamiento propio de la app y abre el selector de
 * "Compartir" de Android (WhatsApp, Drive, Bluetooth, etc.) para que el
 * vendedor/admin lo mande a donde quiera. No pide permisos de almacenamiento
 * porque escribe en el directorio privado de la app + FileProvider.
 *
 * Requiere en AndroidManifest.xml (dentro de <application>):
 *
 * <provider
 *     android:name="androidx.core.content.FileProvider"
 *     android:authorities="${applicationId}.fileprovider"
 *     android:exported="false"
 *     android:grantUriPermissions="true">
 *     <meta-data
 *         android:name="android.support.FILE_PROVIDER_PATHS"
 *         android:resource="@xml/file_paths" />
 * </provider>
 *
 * Y en res/xml/file_paths.xml:
 * <paths xmlns:android="http://schemas.android.com/apk/res/android">
 *     <external-files-path name="csv" path="csv/" />
 * </paths>
 */
object CsvExporter {

    private fun compartirCsv(context: Context, nombreArchivo: String, filas: List<List<String>>) {
        val carpeta = File(context.getExternalFilesDir(null), "csv")
        if (!carpeta.exists()) carpeta.mkdirs()
        val archivo = File(carpeta, nombreArchivo)

        FileWriter(archivo).use { writer ->
            filas.forEach { fila ->
                writer.append(fila.joinToString(",") { campo -> "\"${campo.replace("\"", "\"\"")}\"" })
                writer.append("\n")
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir $nombreArchivo"))
    }

    fun exportarProductos(context: Context, productos: List<org.luisito.gestor360.data.models.Product>) {
        val filas = mutableListOf(listOf("Nombre", "Cantidad", "Ubicación"))
        productos.forEach { p ->
            filas.add(listOf(p.nombre, formatearNumero(p.stock), p.ubicacion ?: ""))
        }
        compartirCsv(context, "productos.csv", filas)
    }

    fun exportarCierreCaja(
        context: Context,
        fecha: String,
        productosVendidos: List<Pair<String, Double>>,
        totalEfectivo: Double,
        totalTransferencia: Double,
        totalMixto: Double
    ) {
        val filas = mutableListOf(listOf("Cierre de caja", fecha))
        filas.add(listOf())
        filas.add(listOf("Producto", "Cantidad vendida"))
        productosVendidos.forEach { (nombre, cantidad) ->
            filas.add(listOf(nombre, formatearNumero(cantidad)))
        }
        filas.add(listOf())
        filas.add(listOf("Resumen de cobros", ""))
        filas.add(listOf("Efectivo", formatearNumero(totalEfectivo)))
        filas.add(listOf("Transferencia", formatearNumero(totalTransferencia)))
        filas.add(listOf("Mixto", formatearNumero(totalMixto)))
        filas.add(listOf("Total", formatearNumero(totalEfectivo + totalTransferencia + totalMixto)))
        compartirCsv(context, "cierre_caja_$fecha.csv", filas)
    }

    private fun formatearNumero(valor: Double): String =
        if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()
}
