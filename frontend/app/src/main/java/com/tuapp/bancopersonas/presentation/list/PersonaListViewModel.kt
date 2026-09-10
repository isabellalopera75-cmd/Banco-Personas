package com.tuapp.bancopersonas.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.data.local.SessionManager
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import com.tuapp.bancopersonas.domain.usecase.EliminarPersonaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListaUiState(
    val busqueda: String = "",
    val pendientes: Int = 0,
    val sinConexion: Boolean = false,
    val mensaje: String? = null,
    /** Se activa cuando el cierre de sesión queda bloqueado por la cola. */
    val bloqueoCierreSesion: String? = null,
)

@HiltViewModel
class PersonaListViewModel @Inject constructor(
    private val repository: PersonaRepository,
    private val eliminarPersona: EliminarPersonaUseCase,
    private val syncScheduler: SyncScheduler,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _estado = MutableStateFlow(ListaUiState())
    val estado: StateFlow<ListaUiState> = _estado.asStateFlow()

    val nombreUsuario: String = sessionManager.nombreUsuario.orEmpty()
    val esAdmin: Boolean = sessionManager.rol == SessionManager.ROL_ADMIN

    val personas: StateFlow<List<Persona>> =
        combine(repository.observarPersonas(), _estado) { lista, estado ->
            val texto = estado.busqueda.trim().lowercase()
            if (texto.isBlank()) lista
            else lista.filter {
                it.nombreCompleto.lowercase().contains(texto) ||
                    it.numeroDocumento.contains(texto)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refrescarPendientes()
        _estado.update { it.copy(sinConexion = !sessionManager.puedeSincronizar) }
    }

    fun cambiarBusqueda(valor: String) = _estado.update { it.copy(busqueda = valor) }

    fun sincronizar() {
        syncScheduler.sincronizarAhora()
        _estado.update { it.copy(mensaje = "Sincronizando…") }
    }

    fun eliminar(id: String) {
        viewModelScope.launch {
            eliminarPersona(id)
            refrescarPendientes()
        }
    }

    fun refrescarPendientes() {
        viewModelScope.launch {
            _estado.update { it.copy(pendientes = repository.cambiosSinEnviar()) }
        }
    }

    fun mensajeConsumido() = _estado.update { it.copy(mensaje = null) }
    fun bloqueoConsumido() = _estado.update { it.copy(bloqueoCierreSesion = null) }

    /**
     * Cierra la sesión y borra la base local.
     *
     * Se niega si la cola no está vacía. Borrar ahí destruiría trabajo de
     * campo que nunca llegó al servidor, y sería exactamente el tipo de
     * pérdida silenciosa que este rediseño vino a eliminar.
     */
    fun cerrarSesion(alSalir: () -> Unit) {
        viewModelScope.launch {
            repository.limpiarDatosLocales()
                .onSuccess {
                    sessionManager.cerrarSesion()
                    alSalir()
                }
                .onFailure { error ->
                    _estado.update { it.copy(bloqueoCierreSesion = error.message) }
                }
        }
    }
}
