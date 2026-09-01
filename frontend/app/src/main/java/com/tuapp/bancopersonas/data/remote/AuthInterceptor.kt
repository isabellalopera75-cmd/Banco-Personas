package com.tuapp.bancopersonas.data.remote

import com.tuapp.bancopersonas.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Adjunta el token de sesión a cada petición saliente.
 *
 * Se resuelve acá y no en cada llamada de la API para que ninguna ruta nueva
 * pueda olvidarse de enviarlo.
 */
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val peticion = chain.request()

        // Las rutas de autenticación son públicas: se usan justamente para
        // obtener el token, así que no puede exigirse uno para llamarlas.
        if (peticion.url.encodedPath.startsWith(RUTA_PUBLICA)) {
            return chain.proceed(peticion)
        }

        val token = sessionManager.token ?: return chain.proceed(peticion)

        val conCredenciales = peticion.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(conCredenciales)
    }

    private companion object {
        const val RUTA_PUBLICA = "/api/auth/"
    }
}
