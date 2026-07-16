package org.luisito.gestor360.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Procesa la foto elegida en la galería antes de guardarla en Room: se
 * recorta al centro para dejarla cuadrada, se reduce a 128x128 y se
 * comprime a JPEG calidad 70. Nunca se sube a Supabase Storage — el
 * ByteArray resultante (unos pocos KB) es lo único que toca disco, dentro
 * de la base de datos local cifrada.
 */
object FotoUtils {
    private const val LADO = 128
    private const val CALIDAD_JPEG = 70

    fun procesarUriAFoto(context: Context, uri: Uri): ByteArray? {
        return try {
            val original = decodificarBitmapMuestreado(context, uri) ?: return null
            val cuadrada = recortarAlCentro(original)
            val redimensionada = Bitmap.createScaledBitmap(cuadrada, LADO, LADO, true)

            val salida = ByteArrayOutputStream()
            redimensionada.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)

            if (redimensionada !== cuadrada) redimensionada.recycle()
            if (cuadrada !== original) cuadrada.recycle()
            original.recycle()

            salida.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decodifica con inSampleSize calculado a partir de los bounds reales,
     * para no cargar en memoria completa una foto de cámara de 12MP antes
     * de recortarla a 128x128 (eso puede tirar OutOfMemory en gama baja).
     */
    private fun decodificarBitmapMuestreado(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null

        var sampleSize = 1
        val menorLado = minOf(bounds.outWidth, bounds.outHeight)
        while (menorLado / (sampleSize * 2) >= LADO) sampleSize *= 2

        val opciones = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
    }

    private fun recortarAlCentro(bitmap: Bitmap): Bitmap {
        val lado = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - lado) / 2
        val y = (bitmap.height - lado) / 2
        return Bitmap.createBitmap(bitmap, x, y, lado, lado)
    }
}
