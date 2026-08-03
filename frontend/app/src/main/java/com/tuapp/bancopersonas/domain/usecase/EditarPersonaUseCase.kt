package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import javax.inject.Inject

class EditarPersonaUseCase @Inject constructor(
    private val repository: PersonaRepository
) {
    suspend operator fun invoke(persona: Persona) {
        repository.editarPersona(persona)
    }
}