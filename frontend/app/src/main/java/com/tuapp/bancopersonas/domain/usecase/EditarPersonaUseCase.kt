package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import javax.inject.Inject

class EditarPersonaUseCase @Inject constructor(
    private val repository: PersonaRepository,
    private val syncScheduler: SyncScheduler
) {
    suspend operator fun invoke(persona: Persona) {
        repository.editarPersona(persona)
        syncScheduler.sincronizarAhora()
    }
}
