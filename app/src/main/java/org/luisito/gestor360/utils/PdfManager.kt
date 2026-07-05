package org.luisito.gestor360.utils

import android.content.Context
import android.graphics.PdfDocument
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

object PdfManager {
    fun exportarProductos(context: Context, productos: List<org.luisito.gestor360.data.models.Product>): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val paint = Paint().apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
        var y = 40f

        canvas.drawText("Gestor360 - Inventario", 20f, y, paint)
        y += 30f
        paint.textSize = 10f; paint.typeface = Typeface.DEFAULT
        productos.forEach { p ->
            canvas.drawText("${p.nombre} - Stock: ${p.stock.toInt()} - ${p.precio} CUP", 20f, y, paint)
            y += 20f
            if (y > 800) { doc.finishPage(page); y = 40f; doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()) }
        }
        doc.finishPage(page)
        val file = File(context.cacheDir, "productos_${System.currentTimeMillis()}.pdf")
        doc.writeTo(FileOutputStream(file))
        doc.close()
        return file
    }
}
