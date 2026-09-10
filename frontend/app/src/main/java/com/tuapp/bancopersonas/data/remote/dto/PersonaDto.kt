package com.tuapp.bancopersonas.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PersonaDto(
    val id: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("numero_documento") val numeroDocumento: String,
    @SerializedName("primer_nombre") val primerNombre: String,
    @SerializedName("segundo_nombre") val segundoNombre: String? = null,
    @SerializedName("primer_apellido") val primerApellido: String,
    @SerializedName("segundo_apellido") val segundoApellido: String? = null,
    @SerializedName("fecha_nacimiento") val fechaNacimiento: String,
    val sexo: String,
    // Nullable porque la columna admite NULL. Declararlo no-nulo hacía que
    // Gson metiera null por reflexión y la app explotara más tarde, lejos del
    // origen real del problema.
    val telefono: String? = null,
    @SerializedName("creado_por") val creadoPor: String? = null,
    val version: Int = 1,
    @SerializedName("cambio_seq") val cambioSeq: Long = 0,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("deleted_at") val deletedAt: String? = null,
)

/** Respuesta de GET /api/personas/sync. */
data class SyncRespuestaDto(
    val personas: List<PersonaDto> = emptyList(),
    val cursor: Long = 0,
    @SerializedName("hay_mas") val hayMas: Boolean = false,
)

/** Cuerpo de POST /api/personas: el alta manda el registro entero. */
data class AltaPersonaDto(
    val id: String,
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("numero_documento") val numeroDocumento: String,
    @SerializedName("primer_nombre") val primerNombre: String,
    @SerializedName("segundo_nombre") val segundoNombre: String? = null,
    @SerializedName("primer_apellido") val primerApellido: String,
    @SerializedName("segundo_apellido") val segundoApellido: String? = null,
    @SerializedName("fecha_nacimiento") val fechaNacimiento: String,
    val sexo: String,
    val telefono: String? = null,
)

/**
 * Cuerpo de PUT /api/personas/:id.
 *
 * Manda SOLO los campos que cambiaron, no el registro entero. Esa es la
 * diferencia que permite el merge: si este dispositivo tocó el teléfono y otro
 * el apellido, los dos cambios conviven. Mandando todo, cualquier edición
 * simultánea sería un choque.
 */
data class EdicionPersonaDto(
    @SerializedName("version_base") val versionBase: Int,
    val cambios: Map<String, String?>,
)

/** Cuerpo de DELETE /api/personas/:id. */
data class BajaPersonaDto(
    @SerializedName("version_base") val versionBase: Int,
)

data class RespuestaPersonaDto(
    val persona: PersonaDto? = null,
    val reintento: Boolean = false,
    val mergeado: Boolean = false,
    @SerializedName("sin_cambios") val sinCambios: Boolean = false,
    @SerializedName("ya_eliminada") val yaEliminada: Boolean = false,
)

data class HistorialDto(
    val id: Long,
    val version: Int,
    val operacion: String,
    val campo: String? = null,
    @SerializedName("valor_anterior") val valorAnterior: String? = null,
    @SerializedName("valor_nuevo") val valorNuevo: String? = null,
    @SerializedName("realizado_en") val realizadoEn: String,
    @SerializedName("realizado_por") val realizadoPor: String,
    @SerializedName("conflicto_id") val conflictoId: String? = null,
)
