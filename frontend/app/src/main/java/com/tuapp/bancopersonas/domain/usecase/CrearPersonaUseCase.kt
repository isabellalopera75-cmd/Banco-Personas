package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import javax.inject.Inject

class CrearPersonaUseCase @Inject constructor(
    private val repository: PersonaRepository,
    private val syncScheduler: SyncScheduler,
) {
    suspend operator fun invoke(persona: Persona): ResultadoGuardado {
        val faltantes = camposFaltantes(persona)
        if (faltantes.isNotEmpty()) return ResultadoGuardado.Incompleto(faltantes)

        // El servidor tiene un índice único sobre el par tipo + número, así
        // que va a rechazar el alta igual. Detectarlo acá convierte un rechazo
        // que llegaría horas después en un aviso inmediato, con el registrador
        // todavía frente a la persona.
        //
        // No reemplaza al control del servidor: otro registrador puede estar
        // dando de alta a la misma persona en este mismo momento, sin señal.
        // Ese caso lo resuelve la cola de conflictos.
        val yaExiste = repository.buscarPorDocumento(persona.tipoDocumento, persona.numeroDocumento)
        if (yaExiste != null) {
            return ResultadoGuardado.DocumentoDuplicado(yaExiste.nombreCompleto)
        }

        repository.crearPersona(persona)

        // Después de guardar, nunca antes: el worker lee la cola al arrancar y
        // una corrida pedida demasiado pronto no vería el alta.
        syncScheduler.sincronizarAhora()

        return ResultadoGuardado.Exito
    }

    private fun camposFaltantes(persona: Persona): List<String> = buildList {
        if (persona.numeroDocumento.isBlank()) add("Número de documento")
        if (persona.primerNombre.isBlank()) add("Primer nombre")
        if (persona.primerApellido.isBlank()) add("Primer apellido")
        if (persona.fechaNacimiento.isBlank()) add("Fecha de nacimiento")
    }
}
