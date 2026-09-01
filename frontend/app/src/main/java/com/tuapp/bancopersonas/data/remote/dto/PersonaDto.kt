package com.tuapp.bancopersonas.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PersonaDto(
    val id: String,
    val nombre: String,
    val documento: String,
    // Nullable: la columna telefono admite NULL en la base del servidor.
    // Declararlo no-nulo hacía que Gson metiera null por reflexión y la app
    // explotara más tarde, lejos del origen real del problema.
    val telefono: String? = null,
    val version: Int,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null
)
