package com.tuapp.bancopersonas.data.remote

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import com.tuapp.bancopersonas.data.remote.dto.PersonaDto

interface PersonaApi {

    // El alta NO vive acá: es pública y va por AuthApi.register, porque
    // quien se registra todavía no tiene token para llamar a este router.

    @GET("api/personas/sync")
    suspend fun obtenerCambios(
        @Query("since") since: String
    ): Response<List<PersonaDto>>

    @PUT("api/personas/{id}")
    suspend fun actualizar(
        @Path("id") id: String,
        @Body body: RequestBody
    ): Response<PersonaDto>

    @DELETE("api/personas/{id}")
    suspend fun eliminar(
        @Path("id") id: String
    ): Response<Unit>

    @GET("api/personas/{id}/historial")
    suspend fun obtenerHistorial(
        @Path("id") id: String
    ): Response<List<com.tuapp.bancopersonas.data.local.entity.HistorialEntity>>

    @GET("api/personas/ganador-semana")
    suspend fun getGanadorSemana(): Response<PersonaDto>
}
