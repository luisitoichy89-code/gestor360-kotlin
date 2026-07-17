package org.luisito.gestor360.data.local
import org.luisito.gestor360.data.local.dao.UserDao

/**
 * Puente delgado entre la UI y UserDao para la foto de perfil. No sabe nada
 * de Compose ni de Supabase: solo lee/escribe el ByteArray en Room.
 */
class FotoRepository(private val userDao: UserDao) {

    suspend fun obtenerFoto(userId: String): ByteArray? =
        userDao.getFoto(userId)

    suspend fun guardarFoto(userId: String, foto: ByteArray) {
        userDao.actualizarFoto(userId, foto)
    }
}
