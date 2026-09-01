package com.tuapp.bancopersonas.data.remote.dto

data class LoginRequest(
    val rol: String,
    val nombre: String? = null,
    val documento: String? = null,
    val password: String
)

data class RegisterRequest(
    val id: String,
    val nombre: String,
    val documento: String,
    val telefono: String? = null,
    val password: String
)

/**
 * Lo que la outbox guarda para un alta pendiente.
 *
 * La contraseña NO viaja acá a propósito: la tabla de Room no está cifrada.
 * Vive en SessionManager, cifrada, hasta el momento de enviarla.
 */
data class RegistroPendiente(
    val id: String,
    val nombre: String,
    val documento: String,
    val telefono: String? = null
)

/** Respuesta común de /api/auth/login y /api/auth/register. */
data class AuthResponse(
    val success: Boolean,
    val rol: String? = null,
    val persona: PersonaDto? = null,
    val token: String? = null,
    val error: String? = null
)
