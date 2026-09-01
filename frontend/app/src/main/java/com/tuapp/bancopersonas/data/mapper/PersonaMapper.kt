package com.tuapp.bancopersonas.data.mapper

import com.tuapp.bancopersonas.data.local.entity.PersonaEntity
import com.tuapp.bancopersonas.data.remote.dto.PersonaDto
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.SyncStatus
import java.time.Instant

// De la base de datos local -> al modelo que usa la pantalla
fun PersonaEntity.toDomain(): Persona {
    return Persona(
        id = id,
        nombre = nombre,
        documento = documento,
        telefono = telefono,
        version = version,
        syncStatus = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.PENDING)
    )
}

// Del DTO del servidor -> al modelo de dominio
fun PersonaDto.toDomain(): Persona {
    return Persona(
        id = id,
        nombre = nombre,
        documento = documento,
        telefono = telefono ?: "",
        version = version,
        syncStatus = SyncStatus.SYNCED // Si viene del server, está sincronizado
    )
}

/**
 * Del DTO del servidor -> directo a Room.
 *
 * Existe aparte de [PersonaDto.toDomain] porque el modelo de dominio no
 * transporta updatedAt ni deletedAt. Pasar por él perdía esos dos campos: una
 * persona dada de baja en el servidor volvía a aparecer activa en el celular,
 * y la fecha real del servidor se reemplazaba por la hora local del teléfono.
 */
fun PersonaDto.toEntity(): PersonaEntity {
    return PersonaEntity(
        id = id,
        nombre = nombre,
        documento = documento,
        telefono = telefono ?: "",
        version = version,
        updatedAt = aEpochMillis(updatedAt) ?: System.currentTimeMillis(),
        syncStatus = SyncStatus.SYNCED.name,
        checksum = "",
        deletedAt = aEpochMillis(deletedAt)
    )
}

// Del modelo de la pantalla -> a como se guarda en Room
fun Persona.toEntity(versionOverride: Int? = null): PersonaEntity {
    return PersonaEntity(
        id = id,
        nombre = nombre,
        documento = documento,
        telefono = telefono,
        version = versionOverride ?: version,
        updatedAt = System.currentTimeMillis(),
        syncStatus = syncStatus.name,
        checksum = ""
    )
}

/** Las fechas del servidor llegan en ISO-8601 UTC. */
private fun aEpochMillis(fecha: String?): Long? =
    fecha?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
