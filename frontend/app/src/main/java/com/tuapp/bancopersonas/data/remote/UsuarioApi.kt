package com.tuapp.bancopersonas.data.remote

import com.tuapp.bancopersonas.data.remote.dto.ActualizarUsuarioRequest
import com.tuapp.bancopersonas.data.remote.dto.CrearUsuarioRequest
import com.tuapp.bancopersonas.data.remote.dto.UsuarioDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Gestión de cuentas. Solo el admin, y solo con conexión. */
interface UsuarioApi {

    @POST("api/usuarios")
    suspend fun crearRegistrador(@Body request: CrearUsuarioRequest): Response<UsuarioDto>

    @GET("api/usuarios")
    suspend fun listar(): Response<List<UsuarioDto>>

    @PATCH("api/usuarios/{id}")
    suspend fun actualizar(
        @Path("id") id: String,
        @Body request: ActualizarUsuarioRequest,
    ): Response<UsuarioDto>
}
