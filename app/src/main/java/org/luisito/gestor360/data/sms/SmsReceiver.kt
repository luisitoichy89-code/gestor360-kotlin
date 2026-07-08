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
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!escuchando) return

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val sender = message.originatingAddress
                val body = message.messageBody

                if (sender == "PAGOxMOVIL") {
                    val monto = extractMonto(body)
                    val coincide = monto == montoEsperado

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
    }

    private fun extractMonto(body: String): Double {
        val regex = Regex("""de\s+(\d+(?:\.\d{1,2})?)\s+CUP""", RegexOption.IGNORE_CASE)
        val match = regex.find(body)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }
}
