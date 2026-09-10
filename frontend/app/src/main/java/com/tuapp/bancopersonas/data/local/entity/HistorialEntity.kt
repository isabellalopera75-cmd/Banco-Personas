package com.tuapp.bancopersonas.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Una entrada de auditoría: UNA FILA POR CAMPO cambiado.
 *
 * El modelo anterior guardaba los tres valores anteriores del registro en una
 * sola fila y el servidor recortaba a los últimos tres cambios. Eso no era
 * auditoría sino una ventana rodante, y no servía para el merge por campo.
 */
@Entity(tableName = "historial_cambios")
data class HistorialEntity(
    @PrimaryKey
    val id: Long,
    val personaId: String,
    val version: Int,
    val operacion: String,
    /** Nulo en CREATE y DELETE: esas operaciones son sobre el registro entero. */
    val campo: String? = null,
    val valorAnterior: String? = null,
    val valorNuevo: String? = null,
    val realizadoPor: String,
    val realizadoEn: String,
    /** Presente si el cambio nació de resolver un conflicto. */
    val conflictoId: String? = null,
)
