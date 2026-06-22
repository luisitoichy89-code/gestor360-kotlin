package org.luisito.gestor360.utils

import java.security.MessageDigest

fun hashSHA256(input: String): String {
    val bytes = input.toByteArray()
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(bytes)
    return hash.joinToString("") { "%02x".format(it) }
}
