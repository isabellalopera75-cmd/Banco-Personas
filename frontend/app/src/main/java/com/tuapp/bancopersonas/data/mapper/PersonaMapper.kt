package com.tuapp.bancopersonas.data.mapper

import com.tuapp.bancopersonas.data.local.entity.PersonaEntity
import com.tuapp.bancopersonas.data.remote.dto.PersonaDto
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.SyncStatus

// De la base de datos local -> al modelo que usa la pantalla
fun PersonaEntity.toDomain(): Persona {
    return Persona(
        id = id,
        nombre = nombre,
        documento = documento,
        telefono = telefono,
        version = version,
        syncStatus = SyncStatus.valueOf(syncStatus)
    )
}

// Del DTO del servidor -> al modelo de dominio
fun PersonaDto.toDomain(): Persona {
    return Persona(
        id = id,
        nombre = nombre,
        documento = documento,
        telefono = telefono,
        version = version,
        syncStatus = SyncStatus.SYNCED // Si viene del server, está sincronizado
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