package com.tuapp.bancopersonas.data.remote

import com.tuapp.bancopersonas.data.remote.dto.AuthResponse
import com.tuapp.bancopersonas.data.remote.dto.LoginRequest
import com.tuapp.bancopersonas.data.remote.dto.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>
}
