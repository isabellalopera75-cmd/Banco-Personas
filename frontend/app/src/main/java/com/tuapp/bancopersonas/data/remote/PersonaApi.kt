package com.tuapp.bancopersonas.data.remote

import com.tuapp.bancopersonas.data.remote.dto.AltaPersonaDto
import com.tuapp.bancopersonas.data.remote.dto.BajaPersonaDto
import com.tuapp.bancopersonas.data.remote.dto.EdicionPersonaDto
import com.tuapp.bancopersonas.data.remote.dto.HistorialDto
import com.tuapp.bancopersonas.data.remote.dto.PersonaDto
import com.tuapp.bancopersonas.data.remote.dto.RespuestaPersonaDto
import com.tuapp.bancopersonas.data.remote.dto.SyncRespuestaDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PersonaApi {

    @POST("api/personas")
    suspend fun crear(@Body body: AltaPersonaDto): Response<RespuestaPersonaDto>

    @PUT("api/personas/{id}")
    suspend fun actualizar(
        @Path("id") id: String,
        @Body body: EdicionPersonaDto,
    ): Response<RespuestaPersonaDto>

    // La baja lleva cuerpo para poder validar la versión, y @DELETE de
    // Retrofit no admite @Body. @HTTP con hasBody es la forma de conseguirlo.
    @HTTP(method = "DELETE", path = "api/personas/{id}", hasBody = true)
    suspend fun eliminar(
        @Path("id") id: String,
        @Body body: BajaPersonaDto,
    ): Response<RespuestaPersonaDto>

    /**
     * Descarga incremental. El cursor es `cambio_seq`, no una fecha: con
     * marcas de tiempo, una transacción que confirma tarde queda por debajo
     * de un cursor ya avanzado y ese cambio no se descarga nunca.
     */
    @GET("api/personas/sync")
    suspend fun obtenerCambios(
        @Query("desde") desde: Long,
        @Query("limite") limite: Int = 500,
    ): Response<SyncRespuestaDto>

    @GET("api/personas/{id}")
    suspend fun obtenerPorId(@Path("id") id: String): Response<PersonaDto>

    @GET("api/personas/{id}/historial")
    suspend fun obtenerHistorial(@Path("id") id: String): Response<List<HistorialDto>>
}
