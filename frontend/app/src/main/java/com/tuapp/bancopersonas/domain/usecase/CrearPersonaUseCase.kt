package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import javax.inject.Inject

class CrearPersonaUseCase @Inject constructor(
    private val repository: PersonaRepository
) {
    suspend operator fun invoke(persona: Persona, password: String) {
        repository.crearPersona(persona, password)
    }
}
