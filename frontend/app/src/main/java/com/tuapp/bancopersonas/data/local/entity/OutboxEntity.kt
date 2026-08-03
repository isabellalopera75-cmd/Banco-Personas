package com.tuapp.bancopersonas.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val outboxId: Long = 0,
    val personaId: String,
    val operacion: String,
    val payload: String,
    val creadoEn: Long,
    val intentos: Int = 0
)