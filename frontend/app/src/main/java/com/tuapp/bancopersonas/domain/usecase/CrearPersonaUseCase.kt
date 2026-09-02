package com.tuapp.bancopersonas.domain.usecase

import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import javax.inject.Inject

class CrearPersonaUseCase @Inject constructor(
    private val repository: PersonaRepository,
    private val syncScheduler: SyncScheduler
) {
    suspend operator fun invoke(persona: Persona, password: String) {
        repository.crearPersona(persona, password)

        // Después de guardar, nunca antes: el worker lee la cola al arrancar y
        // una corrida pedida demasiado pronto no vería el alta.
        //
        // Es el paso que faltaba. Un alta que solo espera la ventana periódica
        // deja al participante sin cuenta en el servidor durante minutos, y su
        // primer intento de entrar termina en un 401.
        syncScheduler.sincronizarAhora()
    }
}
