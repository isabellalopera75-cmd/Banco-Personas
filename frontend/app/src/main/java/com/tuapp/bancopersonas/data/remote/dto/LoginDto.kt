package com.tuapp.bancopersonas.data.remote.dto

import com.google.gson.annotations.SerializedName

/** POST /api/auth/login-operador — admin y registrador. */
data class LoginOperadorRequest(
    val usuario: String,
    val password: String,
)

/**
 * POST /api/auth/login-persona — el rol usuario.
 *
 * No lleva contraseña: la credencial es el propio documento. Es una decisión
 * de negocio tomada con el riesgo advertido, documentada en
 * docs/diseno-tres-roles.md.
 */
data class LoginPersonaRequest(
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("numero_documento") val numeroDocumento: String,
)

data class UsuarioDto(
    val id: String,
    val usuario: String,
    val rol: String,
    val activo: Boolean = true,
    @SerializedName("creado_por") val creadoPor: String? = null,
    @SerializedName("creado_en") val creadoEn: String? = null,
)

data class AuthRespuestaDto(
    val rol: String? = null,
    val token: String? = null,
    val usuario: UsuarioDto? = null,
    val persona: PersonaDto? = null,
    val error: String? = null,
)

/** POST /api/usuarios — el admin crea un registrador. */
data class CrearUsuarioRequest(
    val usuario: String,
    val password: String,
)

data class ActualizarUsuarioRequest(
    val activo: Boolean? = null,
    val password: String? = null,
)
