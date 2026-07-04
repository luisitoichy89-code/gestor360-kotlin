package org.luisito.gestor360.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.luisito.gestor360.data.models.Product
import org.luisito.gestor360.data.models.Turno
import java.io.File
import java.io.FileWriter

object ExportManager {

    fun exportarProductosCSV(context: Context, productos: List<Product>): Uri? {
        val file = File(context.cacheDir, "productos_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            writer.append("Nombre,Precio,Stock,Ubicación,Categoría\n")
            productos.forEach { p ->
                writer.append("\"${p.nombre}\",${p.precio},${p.stock},\"${p.ubicacion ?: ""}\",\"${p.categoria ?: ""}\"\n")
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun exportarTurnosCSV(context: Context, turnos: List<Turno>): Uri? {
        val file = File(context.cacheDir, "turnos_${System.currentTimeMillis()}.csv")
        FileWriter(file).use { writer ->
            writer.append("Apertura,Cierre,Ventas,Efectivo,Transferencia,Diferencia\n")
            turnos.forEach { t ->
                writer.append("\"${t.apertura ?: ""}\",\"${t.cierre ?: ""}\",${t.total_ventas},${t.total_efectivo},${t.total_transferencia},${t.diferencia}\n")
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun compartir(context: Context, uri: Uri, titulo: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, titulo))
    }
}
