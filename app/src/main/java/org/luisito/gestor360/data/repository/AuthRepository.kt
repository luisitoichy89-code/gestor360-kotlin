package org.luisito.gestor360.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luisito.gestor360.data.model.User
import org.luisito.gestor360.data.remote.SupabaseClient
import org.luisito.gestor360.data.room.AppDatabase
import org.luisito.gestor360.data.room.UserDao
import org.luisito.gestor360.utils.hashSHA256
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AuthRepository(private val context: Context) {
    private val db: AppDatabase = AppDatabase.getInstance(context)
    private val userDao: UserDao = db.userDao()
    private val prefs = context.getSharedPreferences("gestor360_config", Context.MODE_PRIVATE)

    suspend fun login(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val user = userDao.getUserByUsername(username)
            if (user == null) {
                return@withContext Result.failure(Exception("Usuario no encontrado"))
            }

            var loginOk = false
            var modo = "offline"

            val clienteId = getClienteId()
            if (clienteId != null) {
                val email = "$username@${clienteId.take(8)}.gestor360.local"
                val onlineOk = SupabaseClient.loginWithEmail(email, password)
                if (onlineOk) {
                    loginOk = true
                    modo = "online"
                    val pwHash = hashSHA256(password)
                    val ahora = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    userDao.updatePassword(user.id, pwHash)
                    userDao.updateLastOnlineLogin(user.id, ahora)
                }
            }

            if (!loginOk && user.password.isNotEmpty()) {
                if (user.password == hashSHA256(password)) {
                    val vencida = if (user.lastOnlineLogin.isNotEmpty()) {
                        try {
                            val ultima = LocalDateTime.parse(user.lastOnlineLogin, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            LocalDateTime.now().minusDays(30) > ultima
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        false
                    }
                    if (!vencida) {
                        loginOk = true
                        modo = "offline"
                    }
                }
            }

            if (loginOk) {
                Result.success(user)
            } else {
                Result.failure(Exception("Contraseña incorrecta"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getClienteId(): String? {
        return prefs.getString("cliente_id", null)
    }
}
