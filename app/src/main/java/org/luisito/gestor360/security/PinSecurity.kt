package org.luisito.gestor360.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashing de PIN para almacenamiento LOCAL (caché offline en Room).
 *
 * Por qué esto existe: antes, el PIN del vendedor llegaba del servidor y se
 * guardaba TAL CUAL en la tabla `users` del dispositivo (UserEntity.pin en
 * texto plano) para poder validar el acceso sin conexión. Eso significa que
 * cualquiera con acceso root o un explorador de archivos en el teléfono
 * (algo nada improbable si el gobierno u otro tercero inspecciona el
 * dispositivo, o si un vendedor deshonesto quiere ver el PIN de un
 * compañero/admin) podía leer el PIN de TODOS los usuarios cacheados
 * directamente de /data/data/.../databases/gestor360.db.
 *
 * Con esto: nunca se guarda el PIN en texto plano localmente. Se guarda
 * solo hash(PIN + salt), con PBKDF2WithHmacSHA256 y 120_000 iteraciones
 * (costoso a propósito para dificultar fuerza bruta offline si alguien
 * logra extraer el archivo de la base de datos). El PIN nunca puede
 * "leerse de vuelta" del hash: solo se puede volver a hashear un intento y
 * comparar.
 */
object PinSecurity {
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"
    private const val ITERACIONES = 120_000
    private const val LONGITUD_LLAVE_BITS = 256
    private const val LONGITUD_SALT_BYTES = 16

    data class PinHasheado(val hash: String, val salt: String)

    fun generarSalt(): String {
        val bytes = ByteArray(LONGITUD_SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Hashea un PIN nuevo generando un salt aleatorio propio. */
    fun hashearPinNuevo(pin: String): PinHasheado {
        val salt = generarSalt()
        return PinHasheado(hash = hashearConSalt(pin, salt), salt = salt)
    }

    fun hashearConSalt(pin: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERACIONES, LONGITUD_LLAVE_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITMO)
        val derivado = factory.generateSecret(spec).encoded
        return Base64.encodeToString(derivado, Base64.NO_WRAP)
    }

    /** Compara un PIN ingresado contra el hash guardado, en tiempo constante. */
    fun verificar(pinIngresado: String, saltBase64: String, hashGuardado: String): Boolean {
        val calculado = hashearConSalt(pinIngresado, saltBase64)
        return constantTimeEquals(calculado, hashGuardado)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ba = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(ba, bb)
    }
}
