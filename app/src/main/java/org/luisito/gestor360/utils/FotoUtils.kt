package org.luisito.gestor360.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream

object FotoUtils {
    private const val TAG = "FotoUtils"
    private const val LADO = 128
    private const val CALIDAD_JPEG = 70

    fun procesarUriAFoto(context: Context, uri: Uri): ByteArray? {
        return try {
            val original = decodificarBitmapMuestreado(context, uri)
            if (original == null) {
                Log.e(TAG, "decodificarBitmapMuestreado devolvió null para uri=$uri")
                return null
            }
            val cuadrada = recortarAlCentro(original)
            val redimensionada = Bitmap.createScaledBitmap(cuadrada, LADO, LADO, true)

            val salida = ByteArrayOutputStream()
            redimensionada.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)

            if (redimensionada !== cuadrada) redimensionada.recycle()
            if (cuadrada !== original) cuadrada.recycle()
            original.recycle()

            salida.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando foto de uri=$uri", e)
            null
        }
    }

    private fun decodificarBitmapMuestreado(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: run {
                Log.e(TAG, "openInputStream devolvió null (sin permiso o uri inválida) para $uri")
                return null
            }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.e(TAG, "BitmapFactory no pudo leer las dimensiones (formato no soportado?) de $uri")
            return null
        }

        var sampleSize = 1
        val menorLado = minOf(bounds.outWidth, bounds.outHeight)
        while (menorLado / (sampleSize * 2) >= LADO) sampleSize *= 2

        val opciones = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
        if (bitmap == null) Log.e(TAG, "El decode final devolvió null para $uri (sampleSize=$sampleSize)")
        return bitmap
    }

    private fun recortarAlCentro(bitmap: Bitmap): Bitmap {
        val lado = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - lado) / 2
        val y = (bitmap.height - lado) / 2
        return Bitmap.createBitmap(bitmap, x, y, lado, lado)
    }

    private const val EDICION_LADO_MAX = 1024

    fun decodificarParaEdicion(context: Context, uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: run { Log.e(TAG, "openInputStream nulo (edición) para $uri"); return null }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.e(TAG, "Bounds inválidos (edición) para $uri")
                return null
            }

            var sampleSize = 1
            val mayorLado = maxOf(bounds.outWidth, bounds.outHeight)
            while (mayorLado / (sampleSize * 2) >= EDICION_LADO_MAX) sampleSize *= 2

            val opciones = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            if (bitmap == null) {
                Log.e(TAG, "Decode de edición devolvió null para $uri")
                return null
            }
            corregirOrientacionExif(context, uri, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error decodificando para edición uri=$uri", e)
            null
        }
    }

    private fun corregirOrientacionExif(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientacion = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val grados = when (orientacion) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (grados == 0f) return bitmap

            val matriz = Matrix().apply { postRotate(grados) }
            val rotado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
            if (rotado !== bitmap) bitmap.recycle()
            rotado
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo leer orientación EXIF, se usa sin rotar", e)
            bitmap
        }
    }

    fun recortarYComprimir(original: Bitmap, region: Rect): ByteArray? {
        return try {
            val ancho = (region.right - region.left).coerceAtLeast(1)
            val alto = (region.bottom - region.top).coerceAtLeast(1)
            val recorte = Bitmap.createBitmap(original, region.left, region.top, ancho, alto)
            val redimensionado = Bitmap.createScaledBitmap(recorte, LADO, LADO, true)

            val salida = ByteArrayOutputStream()
            redimensionado.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)

            if (redimensionado !== recorte) redimensionado.recycle()
            recorte.recycle()

            salida.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error recortando manualmente región=$region", e)
            null
        }
    }
}
