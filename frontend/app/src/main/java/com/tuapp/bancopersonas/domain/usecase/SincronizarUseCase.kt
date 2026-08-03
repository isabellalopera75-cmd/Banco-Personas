package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.repository.PersonaRepository

class SincronizarUseCase(
    private val repository: PersonaRepository
) {
    suspend operator fun invoke() {
        TODO("Not yet implemented")
    }
}
