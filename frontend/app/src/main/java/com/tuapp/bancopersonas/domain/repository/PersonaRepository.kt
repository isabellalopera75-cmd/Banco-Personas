package com.tuapp.bancopersonas.domain.repository

import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.domain.model.Persona
import kotlinx.coroutines.flow.Flow

interface PersonaRepository {
    fun observarPersonas(): Flow<List<Persona>>

    /** El alta crea una cuenta, así que exige contraseña. */
    suspend fun crearPersona(persona: Persona, password: String)

    suspend fun editarPersona(persona: Persona)
    suspend fun eliminarPersona(id: String)
    suspend fun sincronizarHistorial(personaId: String)
    fun observarHistorial(personaId: String): Flow<List<HistorialEntity>>
    suspend fun revertirHistorial(persona: Persona, historial: HistorialEntity)
    suspend fun getGanadorSemana(): Result<Persona>
}
