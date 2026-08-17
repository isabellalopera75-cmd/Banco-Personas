package com.tuapp.bancopersonas.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "historial_cambios")
data class HistorialEntity(
    @PrimaryKey
    val id: String,
    @SerializedName("persona_id")
    val personaId: String,
    @SerializedName("nombre_anterior")
    val nombreAnterior: String,
    @SerializedName("documento_anterior")
    val documentoAnterior: String,
    @SerializedName("telefono_anterior")
    val telefonoAnterior: String,
    @SerializedName("creado_en")
    val creadoEn: String
)
