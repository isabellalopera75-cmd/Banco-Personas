package com.tuapp.bancopersonas.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey
    val id: String,
    val nombre: String,
    val documento: String,
    val telefono: String,
    val version: Int,
    val updatedAt: Long,
    val syncStatus: String,
    val checksum: String,
    val deletedAt: Long? = null
)
