package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import javax.inject.Inject

class EditarPersonaUseCase @Inject constructor(
    private val repository: PersonaRepository,
    private val syncScheduler: SyncScheduler
) {
    suspend operator fun invoke(persona: Persona): ResultadoGuardado {
        val dueño = repository.buscarPorDocumento(persona.documento)

        // Se compara por id y no por existencia: al cambiar solo el teléfono,
        // la búsqueda se encuentra a sí misma, y eso no es un conflicto.
        if (dueño != null && dueño.id != persona.id) {
            return ResultadoGuardado.DocumentoDuplicado
        }

        repository.editarPersona(persona)
        syncScheduler.sincronizarAhora()

        return ResultadoGuardado.Exito
    }
}
