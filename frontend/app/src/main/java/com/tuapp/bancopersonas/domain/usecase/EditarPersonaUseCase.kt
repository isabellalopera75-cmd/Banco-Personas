package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import javax.inject.Inject

class EditarPersonaUseCase @Inject constructor(
    private val repository: PersonaRepository,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(persona: Persona): ResultadoGuardado {
        val duenio = repository.buscarPorDocumento(persona.tipoDocumento, persona.numeroDocumento)

        // Se compara por id y no por existencia: al cambiar solo el teléfono,
        // la búsqueda se encuentra a sí misma, y eso no es un conflicto.
        if (duenio != null && duenio.id != persona.id) {
            return ResultadoGuardado.DocumentoDuplicado(duenio.nombreCompleto)
        }

        repository.editarPersona(persona)
        syncScheduler.sincronizarAhora()

        return ResultadoGuardado.Exito
    }
}
