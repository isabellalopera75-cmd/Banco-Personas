package com.tuapp.bancopersonas.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacenamiento cifrado de la sesión.
 *
 * Se usa EncryptedSharedPreferences y no preferencias comunes porque acá viven
 * credenciales: en un dispositivo con root, unas preferencias normales se leen
 * en texto plano. La clave maestra la respalda el almacén de claves del sistema.
 *
 * Lo que ya NO vive acá son las contraseñas de altas pendientes. Existían
 * porque una persona se registraba sola sin conexión y su contraseña tenía que
 * esperar cifrada hasta poder enviarse. Con registradores eso desapareció: las
 * personas del padrón no tienen contraseña.
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
        set(valor) = prefs.edit().putString(CLAVE_TOKEN, valor).apply()

    /** 'admin', 'registrador' o 'usuario'. */
    var rol: String?
        get() = prefs.getString(CLAVE_ROL, null)
        set(valor) = prefs.edit().putString(CLAVE_ROL, valor).apply()

    /** Id de la cuenta, para admin y registrador. */
    var usuarioId: String?
        get() = prefs.getString(CLAVE_USUARIO_ID, null)
        set(valor) = prefs.edit().putString(CLAVE_USUARIO_ID, valor).apply()

    /** Nombre de usuario del operador, para mostrarlo en pantalla. */
    var nombreUsuario: String?
        get() = prefs.getString(CLAVE_NOMBRE_USUARIO, null)
        set(valor) = prefs.edit().putString(CLAVE_NOMBRE_USUARIO, valor).apply()

    /** Id de la persona del padrón, solo para el rol usuario. */
    var personaId: String?
        get() = prefs.getString(CLAVE_PERSONA_ID, null)
        set(valor) = prefs.edit().putString(CLAVE_PERSONA_ID, valor).apply()

    /**
     * Hay sesión aunque no haya token: un registrador que entró sin conexión
     * tiene rol y puede trabajar, pero no recibió token hasta reconectar.
     * Mirar solo el token dejaría afuera exactamente el caso que la aplicación
     * existe para cubrir.
     */
    val haySesion: Boolean
        get() = rol != null

    val esOperador: Boolean
        get() = rol == ROL_ADMIN || rol == ROL_REGISTRADOR

    val puedeSincronizar: Boolean
        get() = token != null

    val esRegistrador: Boolean
        get() = rol == ROL_REGISTRADOR

    /**
     * Verificador local del operador, para el inicio de sesión sin conexión.
     * Se guarda tras un login online correcto. Ver [LocalPasswordVerifier].
     *
     * Consecuencia operativa: un registrador que sale a campo con la
     * aplicación recién instalada NO puede entrar. Tiene que iniciar sesión
     * con conexión al menos una vez antes de salir.
     */
    fun guardarVerificador(usuario: String, verificador: String) {
        prefs.edit().putString(PREFIJO_VERIFICADOR + usuario.lowercase(), verificador).apply()
    }

    fun obtenerVerificador(usuario: String): String? =
        prefs.getString(PREFIJO_VERIFICADOR + usuario.lowercase(), null)

    /** Datos del operador guardados junto al verificador, para restaurar la sesión sin conexión. */
    fun guardarPerfilOffline(usuario: String, id: String, rolGuardado: String) {
        prefs.edit()
            .putString(PREFIJO_PERFIL_ID + usuario.lowercase(), id)
            .putString(PREFIJO_PERFIL_ROL + usuario.lowercase(), rolGuardado)
            .apply()
    }

    fun perfilOffline(usuario: String): Pair<String, String>? {
        val clave = usuario.lowercase()
        val id = prefs.getString(PREFIJO_PERFIL_ID + clave, null) ?: return null
        val rolGuardado = prefs.getString(PREFIJO_PERFIL_ROL + clave, null) ?: return null
        return id to rolGuardado
    }

    fun abrirSesionOperador(id: String, usuario: String, rolNuevo: String, tokenNuevo: String?) {
        prefs.edit()
            .putString(CLAVE_USUARIO_ID, id)
            .putString(CLAVE_NOMBRE_USUARIO, usuario)
            .putString(CLAVE_ROL, rolNuevo)
            .putString(CLAVE_TOKEN, tokenNuevo)
            .remove(CLAVE_PERSONA_ID)
            .apply()
    }

    fun abrirSesionPersona(idPersona: String, tokenNuevo: String?) {
        prefs.edit()
            .putString(CLAVE_PERSONA_ID, idPersona)
            .putString(CLAVE_ROL, ROL_USUARIO)
            .putString(CLAVE_TOKEN, tokenNuevo)
            .remove(CLAVE_USUARIO_ID)
            .remove(CLAVE_NOMBRE_USUARIO)
            .apply()
    }

    /**
     * Cierra la sesión. NO borra los verificadores: son lo que permite volver
     * a entrar sin conexión, y perderlos dejaría al registrador afuera hasta
     * la próxima vez que tenga señal.
     *
     * El borrado de la base local no se hace acá — lo coordina el repositorio,
     * que además tiene que negarse si la cola de salida no está vacía.
     */
    fun cerrarSesion() {
        prefs.edit()
            .remove(CLAVE_TOKEN)
            .remove(CLAVE_ROL)
            .remove(CLAVE_USUARIO_ID)
            .remove(CLAVE_NOMBRE_USUARIO)
            .remove(CLAVE_PERSONA_ID)
            .apply()
    }

    companion object {
        const val ROL_ADMIN = "admin"
        const val ROL_REGISTRADOR = "registrador"
        const val ROL_USUARIO = "usuario"

        private const val NOMBRE_ARCHIVO = "sesion_segura"
        private const val CLAVE_TOKEN = "token"
        private const val CLAVE_ROL = "rol"
        private const val CLAVE_USUARIO_ID = "usuario_id"
        private const val CLAVE_NOMBRE_USUARIO = "nombre_usuario"
        private const val CLAVE_PERSONA_ID = "persona_id"
        private const val PREFIJO_VERIFICADOR = "verificador_"
        private const val PREFIJO_PERFIL_ID = "perfil_id_"
        private const val PREFIJO_PERFIL_ROL = "perfil_rol_"
    }
}
