package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import javax.inject.Inject

class EliminarPersonaUseCase @Inject constructor(
    private val repository: PersonaRepository,
    private val syncScheduler: SyncScheduler
) {
    suspend operator fun invoke(id: String) {
        repository.eliminarPersona(id)
        syncScheduler.sincronizarAhora()
    }
}
