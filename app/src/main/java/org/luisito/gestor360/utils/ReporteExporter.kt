package org.luisito.gestor360.utils

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import org.luisito.gestor360.data.models.InventarioDia
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReporteExporter {

    private fun generarLineas(dia: InventarioDia): List<String> {
        val generadoEl = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val tot = dia.totales_ventas
        val turno = dia.turno

        val lineas = mutableListOf(
            "Gestor360 - Inventario",
            "Fecha: ${dia.fecha}",
            "Generado el: $generadoEl",
            ""
        )

        // Totales
        lineas.add("TOTALES")
        lineas.add("  Efectivo: ${formatearNumero(tot.efectivo)} CUP")
        lineas.add("  Transferencia: ${formatearNumero(tot.transferencia)} CUP")
        lineas.add("  Total: ${formatearNumero(tot.efectivo + tot.transferencia)} CUP")
        lineas.add("  Ventas: ${tot.cantidad_ventas}")
        if (turno != null) {
            lineas.add("  Apertura: ${formatearNumero(turno.apertura)} CUP")
            turno.cierre?.let { lineas.add("  Cierre: ${formatearNumero(it)} CUP") }
            turno.diferencia?.let { lineas.add("  Diferencia: ${formatearNumero(it)} CUP") }
        }
        lineas.add("")

        // Totales por tarjeta
        if (dia.totales_por_tarjeta.isNotEmpty()) {
            lineas.add("TOTALES POR TARJETA")
            dia.totales_por_tarjeta.forEach { t ->
                lineas.add("  ${t.nombre}: ${formatearNumero(t.total)} CUP")
            }
            lineas.add("")
        }

        // Productos vendidos
        if (dia.productos_vendidos.isNotEmpty()) {
            lineas.add("PRODUCTOS VENDIDOS")
            dia.productos_vendidos.forEach { p ->
                lineas.add("  ${p.nombre}: ${formatearNumero(p.total_vendido)}")
                lineas.add("    Stock actual: ${formatearNumero(p.total_actual)}")
                lineas.add("    Agregado: ${formatearNumero(p.total_agregado)}")
                lineas.add("    Merma: ${formatearNumero(p.total_merma)}")
                lineas.add("    Inicial: ${formatearNumero(p.total_inicial)}")
            }
            lineas.add("")
        }

        // Productos nuevos
        if (dia.productos_nuevos.isNotEmpty()) {
            lineas.add("PRODUCTOS NUEVOS")
            dia.productos_nuevos.forEach { p ->
                lineas.add("  ${p.nombre} (${formatearNumero(p.stock)})")
            }
            lineas.add("")
        }

        // Productos modificados
        if (dia.productos_modificados.isNotEmpty()) {
            lineas.add("PRODUCTOS MODIFICADOS")
            dia.productos_modificados.forEach { p ->
                lineas.add("  ${p.nombre} (${formatearNumero(p.stock)})")
            }
            lineas.add("")
        }

        // Productos eliminados
        if (dia.productos_eliminados.isNotEmpty()) {
            lineas.add("PRODUCTOS ELIMINADOS")
            dia.productos_eliminados.forEach { p ->
                lineas.add("  ${p.nombre} (${formatearNumero(p.stock)})")
            }
            lineas.add("")
        }

        // Mermas
        if (dia.mermas.isNotEmpty()) {
            lineas.add("MERMAS")
            dia.mermas.forEach { m ->
                lineas.add("  ${m.producto_nombre}: ${formatearNumero(m.cantidad)} (${m.estado}) - ${m.motivo}")
            }
            lineas.add("")
        }

        // Devoluciones
        if (dia.devueltos.isNotEmpty()) {
            lineas.add("DEVOLUCIONES")
            dia.devueltos.forEach { d ->
                lineas.add("  ${d.producto_nombre}: ${formatearNumero(d.cantidad)} (${d.estado}) - ${d.metodo}")
            }
            lineas.add("")
        }

        // Ventas con datos de cliente (solo las que tienen tarjeta o datos de cliente)
        val ventasConCliente = dia.ventas.filter { !it.cliente_nombre.isNullOrBlank() || !it.cliente_ci.isNullOrBlank() }
        if (ventasConCliente.isNotEmpty()) {
            lineas.add("PAGOS POR TARJETA")
            ventasConCliente.forEach { v ->
                lineas.add("  ${v.tarjeta_banco ?: "Tarjeta"} · ${v.tarjeta_numero ?: ""}")
                lineas.add("    Producto: ${v.producto_nombre}")
                lineas.add("    Total: ${formatearNumero(v.total)} CUP")
                v.cliente_nombre?.let { lineas.add("    Cliente: $it") }
                v.cliente_ci?.let { lineas.add("    CI: $it") }
                v.cliente_tel?.let { lineas.add("    Tel: $it") }
            }
            lineas.add("")
        }

        return lineas
    }

    private fun formatearNumero(valor: Double): String =
        if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()

    private fun carpetaExport(context: Context): File {
        val carpeta = File(context.getExternalFilesDir(null), "csv")
        if (!carpeta.exists()) carpeta.mkdirs()
        return carpeta
    }

    private fun compartirArchivo(context: Context, archivo: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir ${archivo.name}"))
    }

    // ---------------- TXT ----------------
    fun exportarTxt(context: Context, dia: InventarioDia) {
        val archivo = File(carpetaExport(context), "inventario_${dia.fecha}.txt")
        archivo.writeText(generarLineas(dia).joinToString("\n"))
        compartirArchivo(context, archivo, "text/plain")
    }

    // ---------------- PDF ----------------
    fun exportarPdf(context: Context, dia: InventarioDia) {
        val lineas = generarLineas(dia)
        val pdf = PdfDocument()
        val paintTexto = Paint().apply { textSize = 10f }
        val paintTitulo = Paint().apply { textSize = 14f; isFakeBoldText = true }
        val anchoPagina = 595
        val altoPagina = 842
        val margen = 40f
        val alturaLinea = 14f

        var numeroPagina = 1
        var pagina = pdf.startPage(PdfDocument.PageInfo.Builder(anchoPagina, altoPagina, numeroPagina).create())
        var canvas = pagina.canvas
        var y = margen + 10f

        lineas.forEachIndexed { i, linea ->
            if (y > altoPagina - margen) {
                pdf.finishPage(pagina)
                numeroPagina++
                pagina = pdf.startPage(PdfDocument.PageInfo.Builder(anchoPagina, altoPagina, numeroPagina).create())
                canvas = pagina.canvas
                y = margen + 10f
            }
            canvas.drawText(linea, margen, y, if (i == 0) paintTitulo else paintTexto)
            y += alturaLinea
        }
        pdf.finishPage(pagina)

        val archivo = File(carpetaExport(context), "inventario_${dia.fecha}.pdf")
        FileOutputStream(archivo).use { pdf.writeTo(it) }
        pdf.close()
        compartirArchivo(context, archivo, "application/pdf")
    }

    // ---------------- WORD ----------------
    fun exportarWord(context: Context, dia: InventarioDia) {
        val archivo = File(carpetaExport(context), "inventario_${dia.fecha}.docx")
        escribirDocx(archivo, generarLineas(dia))
        compartirArchivo(context, archivo, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    }

    private fun escribirDocx(archivo: File, lineas: List<String>) {
        ZipOutputStream(FileOutputStream(archivo)).use { zip ->
            fun escribirEntrada(nombre: String, contenido: String) {
                zip.putNextEntry(ZipEntry(nombre))
                zip.write(contenido.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            escribirEntrada("[Content_Types].xml", CONTENT_TYPES_XML)
            escribirEntrada("_rels/.rels", RELS_XML)
            escribirEntrada("word/_rels/document.xml.rels", DOCUMENT_RELS_XML)
            escribirEntrada("word/document.xml", construirDocumentXml(lineas))
        }
    }

    private fun construirDocumentXml(lineas: List<String>): String {
        val cuerpo = lineas.joinToString("") { linea ->
            val escapado = linea.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            "<w:p><w:r><w:t xml:space=\"preserve\">$escapado</w:t></w:r></w:p>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>$cuerpo</w:body></w:document>"
    }

    // ---------------- COMPARTIR ----------------
    fun compartirTexto(context: Context, dia: InventarioDia) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, generarLineas(dia).joinToString("\n"))
        }
        context.startActivity(Intent.createChooser(intent, "Compartir inventario"))
    }

    private const val CONTENT_TYPES_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "</Types>"

    private const val RELS_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "</Relationships>"

    private const val DOCUMENT_RELS_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"></Relationships>"
}
