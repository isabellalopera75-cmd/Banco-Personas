package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObtenerPersonasUseCase @Inject constructor(
    private val repository: PersonaRepository
) {
    operator fun invoke(): Flow<List<Persona>> {
        return repository.observarPersonas()
    }
}