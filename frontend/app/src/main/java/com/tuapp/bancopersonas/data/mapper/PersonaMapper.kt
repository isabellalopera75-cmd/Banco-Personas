package com.tuapp.bancopersonas.data.mapper

import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.data.local.entity.PersonaEntity
import com.tuapp.bancopersonas.data.remote.dto.AltaPersonaDto
import com.tuapp.bancopersonas.data.remote.dto.HistorialDto
import com.tuapp.bancopersonas.data.remote.dto.PersonaDto
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.Sexo
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.model.TipoDocumento
import java.time.Instant

fun PersonaEntity.toDomain(): Persona = Persona(
    id = id,
    // Si la base trajera un valor que la app no conoce, CC es el tipo de
    // documento más frecuente y evita que la pantalla se caiga por un dato.
    tipoDocumento = TipoDocumento.desde(tipoDocumento) ?: TipoDocumento.CC,
    numeroDocumento = numeroDocumento,
    primerNombre = primerNombre,
    segundoNombre = segundoNombre,
    primerApellido = primerApellido,
    segundoApellido = segundoApellido,
    fechaNacimiento = fechaNacimiento,
    sexo = Sexo.desde(sexo) ?: Sexo.I,
    telefono = telefono,
    creadoPor = creadoPor,
    version = version,
    syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.PENDING),
    conflictoId = conflictoId,
)

/**
 * Del servidor directo a Room.
 *
 * No pasa por el modelo de dominio a propósito: ese no transporta cambioSeq,
 * updatedAt ni deletedAt. Pasar por él perdía esos campos y una persona dada
 * de baja en el servidor reaparecía activa en el celular.
 */
fun PersonaDto.toEntity(): PersonaEntity = PersonaEntity(
    id = id,
    tipoDocumento = tipoDocumento,
    numeroDocumento = numeroDocumento,
    primerNombre = primerNombre,
    segundoNombre = segundoNombre,
    primerApellido = primerApellido,
    segundoApellido = segundoApellido,
    fechaNacimiento = fechaNacimiento,
    sexo = sexo,
    telefono = telefono,
    creadoPor = creadoPor,
    version = version,
    cambioSeq = cambioSeq,
    updatedAt = aEpochMillis(updatedAt) ?: System.currentTimeMillis(),
    deletedAt = aEpochMillis(deletedAt),
    syncStatus = SyncStatus.SYNCED.name,
    conflictoId = null,
)

fun Persona.toEntity(
    cambioSeq: Long = 0,
    updatedAt: Long = System.currentTimeMillis(),
    deletedAt: Long? = null,
): PersonaEntity = PersonaEntity(
    id = id,
    tipoDocumento = tipoDocumento.name,
    numeroDocumento = numeroDocumento,
    primerNombre = primerNombre,
    segundoNombre = segundoNombre,
    primerApellido = primerApellido,
    segundoApellido = segundoApellido,
    fechaNacimiento = fechaNacimiento,
    sexo = sexo.name,
    telefono = telefono,
    creadoPor = creadoPor,
    version = version,
    cambioSeq = cambioSeq,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncStatus = syncStatus.name,
    conflictoId = conflictoId,
)

fun PersonaEntity.toAltaDto(): AltaPersonaDto = AltaPersonaDto(
    id = id,
    tipoDocumento = tipoDocumento,
    numeroDocumento = numeroDocumento,
    primerNombre = primerNombre,
    segundoNombre = segundoNombre,
    primerApellido = primerApellido,
    segundoApellido = segundoApellido,
    fechaNacimiento = fechaNacimiento,
    sexo = sexo,
    telefono = telefono,
)

fun HistorialDto.toEntity(personaId: String): HistorialEntity = HistorialEntity(
    id = id,
    personaId = personaId,
    version = version,
    operacion = operacion,
    campo = campo,
    valorAnterior = valorAnterior,
    valorNuevo = valorNuevo,
    realizadoPor = realizadoPor,
    realizadoEn = realizadoEn,
    conflictoId = conflictoId,
)

/** Las fechas del servidor llegan en ISO-8601 UTC. */
private fun aEpochMillis(fecha: String?): Long? =
    fecha?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
