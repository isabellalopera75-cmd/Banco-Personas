package com.tuapp.bancopersonas.data.remote

import com.tuapp.bancopersonas.data.remote.dto.AuthRespuestaDto
import com.tuapp.bancopersonas.data.remote.dto.LoginOperadorRequest
import com.tuapp.bancopersonas.data.remote.dto.LoginPersonaRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    // El registro público desapareció. A las personas las da de alta un
    // registrador autenticado, en PersonaApi.crear.

    @POST("api/auth/login-operador")
    suspend fun loginOperador(@Body request: LoginOperadorRequest): Response<AuthRespuestaDto>

    @POST("api/auth/login-persona")
    suspend fun loginPersona(@Body request: LoginPersonaRequest): Response<AuthRespuestaDto>
}
