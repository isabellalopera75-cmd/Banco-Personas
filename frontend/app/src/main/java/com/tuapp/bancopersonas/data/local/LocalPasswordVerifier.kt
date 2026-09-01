package com.tuapp.bancopersonas.data.local

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verificador de contraseña para el inicio de sesión sin conexión.
 *
 * El servidor nunca envía su hash bcrypt, y hace bien: un hash que viaja
 * es un hash que se puede atacar sin límite de intentos. Para poder validar
 * la contraseña sin internet, la app deriva su propio verificador en el
 * momento en que un login online resulta correcto, y lo guarda cifrado.
 *
 * Se usa PBKDF2 con sal aleatoria por dispositivo: la contraseña en sí
 * nunca queda almacenada, ni siquiera cifrada.
 */
@Singleton
class LocalPasswordVerifier @Inject constructor() {

    /** Devuelve "sal:hash" listo para guardar. */
    fun derivar(password: String): String {
        val sal = ByteArray(LARGO_SAL).also { SecureRandom().nextBytes(it) }
        return "${codificar(sal)}:${codificar(pbkdf2(password, sal))}"
    }

    fun verificar(password: String, verificador: String): Boolean {
        val partes = verificador.split(":")
        if (partes.size != 2) return false

        return try {
            val sal = decodificar(partes[0])
            val esperado = decodificar(partes[1])
            // Comparación de tiempo constante: no revela cuántos bytes coincidieron.
            MessageDigest.isEqual(esperado, pbkdf2(password, sal))
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun pbkdf2(password: String, sal: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), sal, ITERACIONES, LARGO_CLAVE_BITS)
        return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).encoded
    }

    private fun codificar(datos: ByteArray): String = Base64.encodeToString(datos, Base64.NO_WRAP)

    private fun decodificar(texto: String): ByteArray = Base64.decode(texto, Base64.NO_WRAP)

    private companion object {
        const val ALGORITMO = "PBKDF2WithHmacSHA256"
        const val ITERACIONES = 120_000
        const val LARGO_SAL = 16
        const val LARGO_CLAVE_BITS = 256
    }
}
