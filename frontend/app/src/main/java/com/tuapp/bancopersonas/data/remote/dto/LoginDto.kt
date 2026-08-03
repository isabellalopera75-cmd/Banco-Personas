package com.tuapp.bancopersonas.data.remote.dto

data class LoginRequest(
    val rol: String,
    val nombre: String,
    val documento: String? = null,
    val password: String? = null
)

data class LoginResponse(
    val success: Boolean,
    val rol: String? = null,
    val persona: PersonaDto? = null,
    val error: String? = null
)