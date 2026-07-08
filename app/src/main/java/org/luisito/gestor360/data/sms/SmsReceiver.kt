package org.luisito.gestor360.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SmsPagoResult(
    val success: Boolean,
    val monto: Double = 0.0,
    val remitente: String = "",
    val mensaje: String = ""
)

class SmsPagoReceiver : BroadcastReceiver() {

    companion object {
        // OJO: a propósito SIN replay. iniciarEspera() y resultFlow.collect{} se llaman
        // en la misma corrutina sin ningún punto de suspensión entre medio, así que la
        // suscripción ya está activa mucho antes de que pueda llegar un SMS real (eso
        // tarda cientos de ms o segundos vía red+proveedor SMS de Android, no
        // microsegundos). Si aquí pusiéramos replay=1, el próximo cobro por
        // transferencia reproduciría al instante el resultado del cobro ANTERIOR
        // (falso positivo) apenas alguien se suscribiera de nuevo.
        private val _resultFlow = MutableSharedFlow<SmsPagoResult>(extraBufferCapacity = 1)
        val resultFlow: SharedFlow<SmsPagoResult> = _resultFlow.asSharedFlow()

        var montoEsperado: Double = 0.0
        var escuchando: Boolean = false

        fun iniciarEspera(monto: Double) {
            montoEsperado = monto
            escuchando = true
        }

        fun detenerEspera() {
            escuchando = false
            montoEsperado = 0.0
        }

        private const val EPSILON = 0.01

        private fun montosCoinciden(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < EPSILON
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!escuchando) return

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            // Un SMS largo puede llegar partido en varios "PDU"/fragmentos: el body
            // real es la concatenación de todos, no solo el del primer fragmento.
            val body = messages.joinToString("") { it.messageBody ?: "" }
            val sender = messages.firstOrNull()?.originatingAddress ?: ""

            // Antes era "sender == "PAGOxMOVIL"" exacto: cualquier espacio, mayúscula
            // distinta, o que el remitente venga con un prefijo/sufijo (algo común
            // según el operador) hacía que este chequeo fallara silenciosamente y el
            // SMS se ignorara por completo.
            if (sender.trim().equals("PAGOxMOVIL", ignoreCase = true) || sender.contains("PAGOxMOVIL", ignoreCase = true)) {
                val monto = extractMonto(body)
                val coincide = monto > 0.0 && montosCoinciden(monto, montoEsperado)

                _resultFlow.tryEmit(
                    SmsPagoResult(
                        success = coincide,
                        monto = monto,
                        remitente = sender,
                        mensaje = body
                    )
                )

                if (coincide) {
                    escuchando = false
                    montoEsperado = 0.0
                }
            }
        }
    }

    private fun extractMonto(body: String): Double {
        // Antes exigía literalmente la palabra "de" antes del monto ("de 150.00 CUP").
        // Si el texto real de PAGOxMOVIL no trae exactamente esa palabra ahí (ej.
        // "recibido 150.00 CUP", "por 150,00 CUP", o el monto va antes que "CUP" sin
        // preposición), la regex no matcheaba nunca, monto quedaba en 0.0 y el pago
        // jamás se confirmaba por SMS aunque el mensaje sí hubiera llegado. Ahora
        // toma cualquier número (con . o , como separador decimal) seguido de CUP,
        // sin importar qué palabra lo preceda.
        val regex = Regex("""(\d+(?:[.,]\d{1,2})?)\s*CUP""", RegexOption.IGNORE_CASE)
        val match = regex.find(body) ?: return 0.0
        return match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
    }
}
