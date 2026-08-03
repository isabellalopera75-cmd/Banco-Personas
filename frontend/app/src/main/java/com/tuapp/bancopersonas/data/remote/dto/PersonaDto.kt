package com.tuapp.bancopersonas.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PersonaDto(
    val id: String,
    val nombre: String,
    val documento: String,
    val telefono: String,
    val version: Int,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
)