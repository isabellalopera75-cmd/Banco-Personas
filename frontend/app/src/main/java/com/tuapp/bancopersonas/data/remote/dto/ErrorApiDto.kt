package com.tuapp.bancopersonas.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Cuerpo de error de la API.
 *
 * [codigo] es lo que permite distinguir rechazos que comparten el mismo 409
 * pero se tratan al revés. Un ALTA_DUPLICADA quedó guardado como conflicto del
 * lado del servidor y no hay que reintentarlo nunca; un CONFLICTO_VERSION se
 * arregla volviendo a sincronizar. Sin el código, el cliente los trataría
 * igual y en un caso perdería el dato.
 */
data class ErrorApiDto(
    val error: String? = null,
    val codigo: String? = null,
    @SerializedName("conflicto_id") val conflictoId: String? = null,
    @SerializedName("campos_en_conflicto") val camposEnConflicto: List<String>? = null,
    @SerializedName("version_servidor") val versionServidor: Int? = null,
    @SerializedName("persona_servidor") val personaServidor: PersonaDto? = null,
)

/** GET /api/conflictos/mios */
data class ConflictoDto(
    val id: String,
    val tipo: String,
    val estado: String,
    @SerializedName("persona_id_cliente") val personaIdCliente: String? = null,
    @SerializedName("persona_existente_id") val personaExistenteId: String? = null,
    @SerializedName("enviado_en") val enviadoEn: String? = null,
    @SerializedName("resuelto_en") val resueltoEn: String? = null,
    @SerializedName("nota_resolucion") val notaResolucion: String? = null,
)

data class ConflictosRespuestaDto(
    val conflictos: List<ConflictoDto> = emptyList(),
)

/** Una fila de la cola del administrador. */
data class ConflictoAdminDto(
    val id: String,
    val tipo: String,
    val estado: String,
    @SerializedName("datos_enviados") val datosEnviados: Map<String, String?> = emptyMap(),
    @SerializedName("version_base") val versionBase: Int? = null,
    @SerializedName("persona_id_cliente") val personaIdCliente: String? = null,
    @SerializedName("enviado_por") val enviadoPor: String? = null,
    @SerializedName("enviado_en") val enviadoEn: String? = null,
    @SerializedName("resuelto_por") val resueltoPor: String? = null,
    @SerializedName("nota_resolucion") val notaResolucion: String? = null,
    @SerializedName("persona_existente") val personaExistente: PersonaDto? = null,
)

data class ConflictosAdminRespuestaDto(
    val conflictos: List<ConflictoAdminDto> = emptyList(),
)

data class FusionarRequest(
    val campos: Map<String, String?>,
    val nota: String? = null,
)

data class CrearComoNuevaRequest(
    @SerializedName("tipo_documento") val tipoDocumento: String,
    @SerializedName("numero_documento") val numeroDocumento: String,
    val nota: String? = null,
)

data class DescartarRequest(val nota: String)
