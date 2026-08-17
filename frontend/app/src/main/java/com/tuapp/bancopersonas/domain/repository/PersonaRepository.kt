package com.tuapp.bancopersonas.domain.repository

import com.tuapp.bancopersonas.domain.model.Persona
import kotlinx.coroutines.flow.Flow

interface PersonaRepository {
    fun observarPersonas(): Flow<List<Persona>>
    suspend fun crearPersona(persona: Persona)
    suspend fun editarPersona(persona: Persona)
    suspend fun eliminarPersona(id: String)
    suspend fun sincronizarHistorial(personaId: String)
    fun observarHistorial(personaId: String): Flow<List<com.tuapp.bancopersonas.data.local.entity.HistorialEntity>>
    suspend fun revertirHistorial(persona: Persona, historial: com.tuapp.bancopersonas.data.local.entity.HistorialEntity)
    suspend fun getGanadorSemana(): Result<Persona>
}
