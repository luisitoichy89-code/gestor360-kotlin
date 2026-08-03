package org.luisito.gestor360.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.luisito.gestor360.data.models.InventarioDia
import java.io.File
import java.io.FileWriter

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

    fun exportarInventario(context: Context, dia: InventarioDia) {
        val generadoEl = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val tot = dia.totales_ventas
        val turno = dia.turno

        val filas = mutableListOf(
            listOf("Gestor360 · Inventario"),
            listOf("Fecha", dia.fecha ?: ""),
            listOf("Generado el", generadoEl),
            listOf("")
        )

        // Totales
        filas.add(listOf("TOTALES", ""))
        filas.add(listOf("Efectivo", formatearNumero(tot.efectivo)))
        filas.add(listOf("Transferencia", formatearNumero(tot.transferencia)))
        filas.add(listOf("Total", formatearNumero(tot.efectivo + tot.transferencia)))
        filas.add(listOf("Cantidad de ventas", tot.cantidad_ventas.toString()))
        if (turno != null) {
            filas.add(listOf("Apertura", formatearNumero(turno.apertura)))
            turno.cierre?.let { filas.add(listOf("Cierre", formatearNumero(it))) }
            turno.diferencia?.let { filas.add(listOf("Diferencia", formatearNumero(it))) }
        }
        filas.add(listOf(""))

        // Totales por tarjeta
        if (dia.totales_por_tarjeta.isNotEmpty()) {
            filas.add(listOf("TOTALES POR TARJETA", ""))
            dia.totales_por_tarjeta.forEach { t ->
                filas.add(listOf(t.nombre, formatearNumero(t.total)))
            }
            filas.add(listOf(""))
        }

        // Productos vendidos
        if (dia.productos_vendidos.isNotEmpty()) {
            filas.add(listOf("PRODUCTOS VENDIDOS", "Total vendido", "Stock actual", "Agregado", "Merma", "Inicial"))
            dia.productos_vendidos.forEach { p ->
                filas.add(listOf(p.nombre, formatearNumero(p.total_vendido), formatearNumero(p.total_actual), formatearNumero(p.total_agregado), formatearNumero(p.total_merma), formatearNumero(p.total_inicial)))
            }
            filas.add(listOf(""))
        }

        // Productos nuevos
        if (dia.productos_nuevos.isNotEmpty()) {
            filas.add(listOf("PRODUCTOS NUEVOS", "Stock"))
            dia.productos_nuevos.forEach { p ->
                filas.add(listOf(p.nombre, formatearNumero(p.stock)))
            }
            filas.add(listOf(""))
        }

        // Productos modificados
        if (dia.productos_modificados.isNotEmpty()) {
            filas.add(listOf("PRODUCTOS MODIFICADOS", "Stock"))
            dia.productos_modificados.forEach { p ->
                filas.add(listOf(p.nombre, formatearNumero(p.stock)))
            }
            filas.add(listOf(""))
        }

        // Productos eliminados
        if (dia.productos_eliminados.isNotEmpty()) {
            filas.add(listOf("PRODUCTOS ELIMINADOS", "Stock"))
            dia.productos_eliminados.forEach { p ->
                filas.add(listOf(p.nombre, formatearNumero(p.stock)))
            }
            filas.add(listOf(""))
        }

        // Mermas
        if (dia.mermas.isNotEmpty()) {
            filas.add(listOf("MERMAS", "Cantidad", "Estado", "Motivo"))
            dia.mermas.forEach { m ->
                filas.add(listOf(m.producto_nombre, formatearNumero(m.cantidad), m.estado, m.motivo))
            }
            filas.add(listOf(""))
        }

        // Devoluciones
        if (dia.devueltos.isNotEmpty()) {
            filas.add(listOf("DEVOLUCIONES", "Cantidad", "Estado", "Método"))
            dia.devueltos.forEach { d ->
                filas.add(listOf(d.producto_nombre, formatearNumero(d.cantidad), d.estado, d.metodo))
            }
            filas.add(listOf(""))
        }

        // Pagos por tarjeta
        val ventasConCliente = dia.ventas.filter { !it.cliente_nombre.isNullOrBlank() || !it.cliente_ci.isNullOrBlank() }
        if (ventasConCliente.isNotEmpty()) {
            filas.add(listOf("PAGOS POR TARJETA", "Producto", "Total", "Cliente", "CI", "Teléfono", "Tarjeta"))
            ventasConCliente.forEach { v ->
                filas.add(listOf(
                    v.tarjeta_banco ?: "", v.producto_nombre, formatearNumero(v.total),
                    v.cliente_nombre ?: "", v.cliente_ci ?: "", v.cliente_tel ?: "",
                    v.tarjeta_numero ?: ""
                ))
            }
            filas.add(listOf(""))
        }

        compartirCsv(context, "inventario_${dia.fecha ?: "sin_fecha"}.csv", filas)
    }

    private fun formatearNumero(valor: Double): String =
        if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()
}
