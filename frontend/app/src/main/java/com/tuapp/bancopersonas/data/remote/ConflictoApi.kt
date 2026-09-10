package com.tuapp.bancopersonas.data.remote

import com.tuapp.bancopersonas.data.remote.dto.ConflictoAdminDto
import com.tuapp.bancopersonas.data.remote.dto.ConflictosAdminRespuestaDto
import com.tuapp.bancopersonas.data.remote.dto.ConflictosRespuestaDto
import com.tuapp.bancopersonas.data.remote.dto.CrearComoNuevaRequest
import com.tuapp.bancopersonas.data.remote.dto.DescartarRequest
import com.tuapp.bancopersonas.data.remote.dto.FusionarRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ConflictoApi {

    /**
     * Qué pasó con lo que este registrador mandó y el servidor no pudo
     * aplicar. Sin esta consulta, un registro marcado EN_REVISION en el
     * teléfono quedaría así para siempre: el dispositivo no tiene otra forma
     * de enterarse de que el admin ya decidió.
     */
    @GET("api/conflictos/mios")
    suspend fun mios(): Response<ConflictosRespuestaDto>

    // --- Solo el admin ----------------------------------------------------

    @GET("api/conflictos")
    suspend fun listar(
        @Query("estado") estado: String = "PENDIENTE",
    ): Response<ConflictosAdminRespuestaDto>

    @POST("api/conflictos/{id}/fusionar")
    suspend fun fusionar(
        @Path("id") id: String,
        @Body body: FusionarRequest,
    ): Response<Map<String, Any>>

    @POST("api/conflictos/{id}/crear-como-nueva")
    suspend fun crearComoNueva(
        @Path("id") id: String,
        @Body body: CrearComoNuevaRequest,
    ): Response<Map<String, Any>>

    @POST("api/conflictos/{id}/descartar")
    suspend fun descartar(
        @Path("id") id: String,
        @Body body: DescartarRequest,
    ): Response<Map<String, Any>>
}
