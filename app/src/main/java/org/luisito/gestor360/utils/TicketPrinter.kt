package org.luisito.gestor360.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import org.luisito.gestor360.data.models.CartItem

/**
 * Impresión de tickets en impresora térmica Bluetooth (ESC/POS), usando
 * DantSu/ESCPOS-ThermalPrinter-Android. Antes de llamar a imprimirTicket()
 * hay que haber pedido y tener concedidos los permisos BLUETOOTH_CONNECT
 * (Android 12+) / BLUETOOTH (Android 11 o menor) y que el teléfono ya esté
 * emparejado con la impresora por Bluetooth (eso se hace una vez, desde los
 * ajustes de Android, no desde la app).
 */
object TicketPrinter {

    /** Impresoras Bluetooth ya emparejadas que aparentan ser térmicas. Para mostrar un selector si hay varias. */
    fun listarImpresorasEmparejadas(): List<BluetoothDevice> {
        return try {
            BluetoothPrintersConnections.getBluetoothPrinters()?.mapNotNull { it.device } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Imprime el ticket. Si no se especifica "dispositivo", usa la primera
     * impresora Bluetooth emparejada que encuentre (sirve para el caso común
     * de "una sola impresora en el negocio").
     */
    fun imprimirTicket(
        nombreNegocio: String,
        carrito: List<CartItem>,
        total: Double,
        metodo: String,
        vendedor: String,
        dispositivo: BluetoothDevice? = null
    ): Result<Unit> {
        return try {
            val conexion: BluetoothConnection = if (dispositivo != null) {
                BluetoothConnection(dispositivo)
            } else {
                BluetoothPrintersConnections.selectFirstPaired()
                    ?: return Result.failure(IllegalStateException("No hay ninguna impresora Bluetooth emparejada. Empareja una desde los ajustes de Android primero."))
            }

            // 384 puntos ~ 58mm, 32 caracteres por línea con la fuente por defecto.
            val impresora = EscPosPrinter(conexion, 203, 48f, 32)

            val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val metodoTexto = when (metodo) {
                "cash" -> "Efectivo"
                "transfer" -> "Transferencia"
                "mixed" -> "Mixto"
                else -> metodo
            }

            val texto = buildString {
                append("[C]<b><font size='big'>${nombreNegocio.uppercase()}</font></b>\n")
                append("[C]--------------------------------\n")
                append("[L]Fecha: $fecha\n")
                append("[L]Vendedor: $vendedor\n")
                append("[C]--------------------------------\n")
                carrito.forEach { item ->
                    append("[L]${item.nombre}\n")
                    append("[L]  ${formatearCantidad(item.cantidad)} x ${item.precio} CUP[R]${formatearCantidad(item.subtotal)} CUP\n")
                }
                append("[C]--------------------------------\n")
                append("[L]<b>TOTAL[R]$total CUP</b>\n")
                append("[L]Método: $metodoTexto\n")
                append("[C]--------------------------------\n")
                append("[C]<font size='small'>¡Gracias por su compra!</font>\n")
                append("[C]\n\n")
            }

            impresora.printFormattedTextAndCut(texto)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatearCantidad(valor: Double): String =
        if (valor == valor.toLong().toDouble()) valor.toLong().toString() else valor.toString()
}
