package com.tuapp.bancopersonas.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "personas",
    indices = [
        // Igual que en el servidor: el documento identifica por el PAR, no
        // por el número solo. Una CC y una TI pueden compartir número.
        Index(value = ["tipoDocumento", "numeroDocumento"]),
        Index(value = ["cambioSeq"]),
    ]
)
data class PersonaEntity(
    @PrimaryKey
    val id: String,

    val tipoDocumento: String,
    val numeroDocumento: String,

    val primerNombre: String,
    val segundoNombre: String? = null,
    val primerApellido: String,
    val segundoApellido: String? = null,

    val fechaNacimiento: String,
    val sexo: String,
    val telefono: String? = null,

    val creadoPor: String? = null,

    /** Última versión conocida del servidor. No se incrementa localmente. */
    val version: Int = 1,

    /** Cursor del servidor. Se guarda para saber hasta dónde se sincronizó. */
    val cambioSeq: Long = 0,

    val updatedAt: Long = 0,
    val deletedAt: Long? = null,

    val syncStatus: String,

    /** Id del conflicto que dejó este registro esperando al admin. */
    val conflictoId: String? = null,
)
