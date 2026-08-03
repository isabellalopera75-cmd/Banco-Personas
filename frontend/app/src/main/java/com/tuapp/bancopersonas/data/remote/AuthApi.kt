package com.tuapp.bancopersonas.data.remote

import com.tuapp.bancopersonas.data.remote.dto.LoginRequest
import com.tuapp.bancopersonas.data.remote.dto.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}