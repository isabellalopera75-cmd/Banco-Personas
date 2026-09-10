package com.tuapp.bancopersonas.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cola de salida: lo que este dispositivo hizo sin conexión y todavía no pudo
 * enviar. Transporta exactamente tres operaciones: CREATE, UPDATE y DELETE.
 *
 * El índice sobre personaId no es de rendimiento sino de corrección: antes de
 * encolar hay que buscar si ya hay algo pendiente para esa persona y unificarlo.
 * Dos UPDATE sueltos de la misma persona viajarían con la misma version_base y
 * el segundo entraría en conflicto contra el primero — contra sí mismo.
 */
@Entity(
    tableName = "outbox",
    indices = [Index(value = ["personaId"])]
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val outboxId: Long = 0,
    val personaId: String,
    val operacion: String,
    val payload: String,
    val creadoEn: Long,
    val intentos: Int = 0,
)
