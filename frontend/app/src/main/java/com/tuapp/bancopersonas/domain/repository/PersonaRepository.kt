package com.tuapp.bancopersonas.domain.repository

import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.TipoDocumento
import kotlinx.coroutines.flow.Flow

interface PersonaRepository {

    /** El padrón que este dispositivo tiene descargado. */
    fun observarPersonas(): Flow<List<Persona>>

    /** Una sola persona. Es lo único que necesita el rol usuario. */
    fun observarPersona(id: String): Flow<Persona?>

    suspend fun obtenerPorId(id: String): Persona?

    /**
     * Busca por el PAR tipo + número, no por el número solo. Una CC y una TI
     * pueden compartir número y son personas distintas.
     *
     * Permite avisar de un documento repetido antes de encolar el alta, en vez
     * de descubrirlo cuando el servidor la rechaza y el registrador ya se fue.
     */
    suspend fun buscarPorDocumento(tipo: TipoDocumento, numero: String): Persona?

    suspend fun crearPersona(persona: Persona)

    suspend fun editarPersona(persona: Persona)

    suspend fun eliminarPersona(id: String)

    suspend fun sincronizarHistorial(personaId: String)

    fun observarHistorial(personaId: String): Flow<List<HistorialEntity>>

    /** Deshace un cambio puntual volviendo el campo a su valor anterior. */
    suspend fun revertirCambio(persona: Persona, historial: HistorialEntity)

    /** Cuántas operaciones quedan sin enviar. Bloquea el cierre de sesión. */
    suspend fun cambiosSinEnviar(): Int

    /**
     * Borra todo lo que este dispositivo tiene guardado.
     *
     * Se llama al cerrar sesión: si no, los datos de la persona anterior
     * quedan visibles para quien entre después en el mismo teléfono, y son
     * datos de salud.
     *
     * Falla si la cola de salida no está vacía: borrar ahí destruiría trabajo
     * de campo que nunca llegó al servidor.
     */
    suspend fun limpiarDatosLocales(): Result<Unit>
}
