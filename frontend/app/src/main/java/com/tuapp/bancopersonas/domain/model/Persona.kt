package com.tuapp.bancopersonas.domain.model

data class Persona(
    val id: String,
    val nombre: String,
    val documento: String,
    val telefono: String,
    val version: Int = 1,
    val syncStatus: SyncStatus
)

enum class SyncStatus {
    SYNCED,
    PENDING,
    CONFLICT
}
