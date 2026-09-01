package com.tuapp.bancopersonas.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacenamiento cifrado de la sesión y de las contraseñas que todavía no
 * se pudieron enviar al servidor.
 *
 * Se usa EncryptedSharedPreferences y no SharedPreferences comunes porque
 * aquí viven credenciales: en un dispositivo con root, unas preferencias
 * normales se leen en texto plano. La clave maestra la respalda el
 * almacén de claves del sistema.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        NOMBRE_ARCHIVO,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var token: String?
        get() = prefs.getString(CLAVE_TOKEN, null)
        set(valor) {
            prefs.edit().putString(CLAVE_TOKEN, valor).apply()
        }

    var rol: String?
        get() = prefs.getString(CLAVE_ROL, null)
        set(valor) {
            prefs.edit().putString(CLAVE_ROL, valor).apply()
        }

    var personaId: String?
        get() = prefs.getString(CLAVE_PERSONA_ID, null)
        set(valor) {
            prefs.edit().putString(CLAVE_PERSONA_ID, valor).apply()
        }

    val haySesion: Boolean
        get() = token != null

    /**
     * Cursor de la última sincronización, en ISO-8601 tal como lo emitió el
     * servidor. Se guarda el valor del servidor y no la hora local a propósito:
     * si el reloj del teléfono está corrido, un cursor local perdería cambios.
     */
    var ultimaSincronizacion: String?
        get() = prefs.getString(CLAVE_ULTIMA_SYNC, null)
        set(valor) {
            prefs.edit().putString(CLAVE_ULTIMA_SYNC, valor).apply()
        }

    /**
     * Contraseña de un alta hecha sin conexión.
     *
     * No se guarda en la outbox: esa tabla de Room no está cifrada. Vive acá,
     * cifrada, hasta que la sincronización logra enviarla al servidor, y se
     * borra en cuanto eso ocurre.
     */
    fun guardarPasswordPendiente(personaId: String, password: String) {
        prefs.edit().putString(PREFIJO_PASSWORD + personaId, password).apply()
    }

    fun obtenerPasswordPendiente(personaId: String): String? =
        prefs.getString(PREFIJO_PASSWORD + personaId, null)

    fun borrarPasswordPendiente(personaId: String) {
        prefs.edit().remove(PREFIJO_PASSWORD + personaId).apply()
    }

    /**
     * Verificador local de contraseña, usado para el login sin conexión.
     * Se guarda tras un login online correcto. Ver [LocalPasswordVerifier].
     */
    fun guardarVerificador(documento: String, verificador: String) {
        prefs.edit().putString(PREFIJO_VERIFICADOR + documento, verificador).apply()
    }

    fun obtenerVerificador(documento: String): String? =
        prefs.getString(PREFIJO_VERIFICADOR + documento, null)

    /**
     * Cierra la sesión. No toca las contraseñas pendientes: siguen
     * representando altas que el servidor todavía no recibió.
     */
    fun cerrarSesion() {
        prefs.edit()
            .remove(CLAVE_TOKEN)
            .remove(CLAVE_ROL)
            .remove(CLAVE_PERSONA_ID)
            .apply()
    }

    private companion object {
        const val NOMBRE_ARCHIVO = "sesion_segura"
        const val CLAVE_TOKEN = "token"
        const val CLAVE_ROL = "rol"
        const val CLAVE_PERSONA_ID = "persona_id"
        const val PREFIJO_PASSWORD = "password_pendiente_"
        const val PREFIJO_VERIFICADOR = "verificador_"
        const val CLAVE_ULTIMA_SYNC = "ultima_sincronizacion"
    }
}
