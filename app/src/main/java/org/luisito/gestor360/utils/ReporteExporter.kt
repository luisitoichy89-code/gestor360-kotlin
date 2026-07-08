package org.luisito.gestor360.utils

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exporta el cierre de caja en 4 formatos: PDF, TXT, Word (.docx) y Compartir
 * (texto plano directo, sin archivo — para pegar en WhatsApp/Telegram rápido).
 *
 * Los 4 parten de la MISMA función generarLineas(), que solo usa los datos que
 * ya trae CierreCajaUiState del turno activo (productos vendidos, totales,
 * apertura). No se agrega nada de otros turnos ni historial: cada archivo
 * "solo manda lo afectado" por el cierre que se está exportando en ese momento.
 *
 * PDF y Word se generan sin librerías externas (android.graphics.pdf.PdfDocument
 * es parte del SDK de Android; el .docx es simplemente un .zip con XML interno,
 * así que se arma a mano con java.util.zip). No hace falta tocar build.gradle.kts.
 */
object ReporteExporter {

    data class DatosCierreCaja(
        val fecha: String,
        val productosVendidos: List<Pair<String, Double>>,
        val totalEfectivo: Double,
        val totalTransferencia: Double,
        val totalMixto: Double,
        val totalMixtoEfectivo: Double,
        val totalMixtoTransferencia: Double,
        val apertura: Double
    )

    private fun generarLineas(d: DatosCierreCaja): List<String> {
        val generadoEl = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val efectivoEnCaja = d.totalEfectivo + d.totalMixtoEfectivo
        val lineas = mutableListOf(
            "Gestor360 - Cierre de caja",
            "Fecha del turno: ${d.fecha}",
            "Generado el: $generadoEl",
            ""
        )
        lineas.add("PRODUCTOS VENDIDOS")
        if (d.productosVendidos.isEmpty()) lineas.add("  (sin ventas en este turno)")
        d.productosVendidos.forEach { (nombre, cantidad) -> lineas.add("  $nombre: ${formatearNumero(cantidad)}") }
        lineas.add("")
        lineas.add("RESUMEN DE COBROS")
        lineas.add("  Apertura: ${formatearNumero(d.apertura)} CUP")
        lineas.add("  Efectivo (ventas 100% efectivo): ${formatearNumero(d.totalEfectivo)} CUP")
        lineas.add("  Transferencia: ${formatearNumero(d.totalTransferencia)} CUP")
        lineas.add("  Mixto (total): ${formatearNumero(d.totalMixto)} CUP")
        lineas.add("    - Mixto en efectivo: ${formatearNumero(d.totalMixtoEfectivo)} CUP")
        lineas.add("    - Mixto en transferencia: ${formatearNumero(d.totalMixtoTransferencia)} CUP")
        lineas.add("")
        lineas.add("Efectivo esperado en caja: ${formatearNumero(d.apertura + efectivoEnCaja)} CUP")
        lineas.add("Total general vendido: ${formatearNumero(d.totalEfectivo + d.totalTransferencia + d.totalMixto)} CUP")
        return lineas
    }

    private fun formatearNumero(valor: Double): String =
        if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()

    private fun carpetaExport(context: Context): File {
        // OJO: se reutiliza la MISMA carpeta "csv/" que ya usa CsvExporter, no una
        // carpeta nueva. res/xml/file_paths.xml solo tiene declarada esa ruta para
        // el FileProvider; si se escribe en una carpeta distinta sin declararla ahí,
        // FileProvider.getUriForFile() lanza IllegalArgumentException apenas se llama
        // y la app se cierra sola (eso rompía PDF/TXT/Word: "Compartir" no falla
        // porque no toca FileProvider, es solo texto plano).
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
    fun exportarTxt(context: Context, datos: DatosCierreCaja) {
        val archivo = File(carpetaExport(context), "cierre_caja_${datos.fecha}.txt")
        archivo.writeText(generarLineas(datos).joinToString("\n"))
        compartirArchivo(context, archivo, "text/plain")
    }

    // ---------------- PDF ----------------
    fun exportarPdf(context: Context, datos: DatosCierreCaja) {
        val lineas = generarLineas(datos)
        val pdf = PdfDocument()
        val paintTexto = Paint().apply { textSize = 11f }
        val paintTitulo = Paint().apply { textSize = 15f; isFakeBoldText = true }
        // Tamaño A4 en puntos (72 dpi): 595 x 842
        val anchoPagina = 595
        val altoPagina = 842
        val margen = 40f
        val alturaLinea = 16f

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

        val archivo = File(carpetaExport(context), "cierre_caja_${datos.fecha}.pdf")
        FileOutputStream(archivo).use { pdf.writeTo(it) }
        pdf.close()
        compartirArchivo(context, archivo, "application/pdf")
    }

    // ---------------- WORD (.docx mínimo, sin Apache POI) ----------------
    fun exportarWord(context: Context, datos: DatosCierreCaja) {
        val archivo = File(carpetaExport(context), "cierre_caja_${datos.fecha}.docx")
        escribirDocx(archivo, generarLineas(datos))
        compartirArchivo(context, archivo, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    }

    private fun escribirDocx(archivo: File, lineas: List<String>) {
        // Un .docx válido es, por dentro, solo un .zip con estas 4 piezas mínimas.
        // No hace falta ninguna librería (Apache POI, etc.) para esto tan simple.
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

    // ---------------- COMPARTIR (texto plano directo, sin archivo) ----------------
    fun compartirTexto(context: Context, datos: DatosCierreCaja) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, generarLineas(datos).joinToString("\n"))
        }
        context.startActivity(Intent.createChooser(intent, "Compartir cierre de caja"))
    }
}
