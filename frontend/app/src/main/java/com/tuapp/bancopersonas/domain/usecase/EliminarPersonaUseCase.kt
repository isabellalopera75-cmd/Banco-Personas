package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import javax.inject.Inject

class EliminarPersonaUseCase @Inject constructor(
    private val repository: PersonaRepository
) {
    suspend operator fun invoke(id: String) {
        repository.eliminarPersona(id)
    }
}