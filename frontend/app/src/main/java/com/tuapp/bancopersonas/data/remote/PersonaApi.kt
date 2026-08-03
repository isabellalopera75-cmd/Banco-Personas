package com.tuapp.bancopersonas.data.remote

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import com.tuapp.bancopersonas.data.remote.dto.PersonaDto

interface PersonaApi {

    @GET("api/personas")
    suspend fun obtenerCambios(
        @Query("since") since: String
    ): Response<List<PersonaDto>>

    @POST("api/personas")
    suspend fun crear(
        @Body body: RequestBody
    ): Response<PersonaDto>

    @PUT("api/personas/{id}")
    suspend fun actualizar(
        @Path("id") id: String,
        @Body body: RequestBody
    ): Response<PersonaDto>

    @DELETE("api/personas/{id}")
    suspend fun eliminar(
        @Path("id") id: String
    ): Response<Unit>
}