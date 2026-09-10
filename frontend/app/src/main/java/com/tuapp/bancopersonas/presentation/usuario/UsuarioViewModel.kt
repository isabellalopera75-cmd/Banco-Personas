package com.tuapp.bancopersonas.presentation.usuario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.data.local.SessionManager
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.data.remote.PersonaApi
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsuarioUiState(
    val sinConexion: Boolean = false,
    val actualizando: Boolean = false,
)

/**
 * La pantalla del rol usuario: sus propios datos, de solo lectura.
 *
 * Es la ruta más simple de las tres. Como no escribe nunca, no necesita cola
 * de salida ni versión base, y no puede generar un conflicto ni queriendo. Lo
 * único que guarda este dispositivo es una fila: la suya.
 */
@HiltViewModel
class UsuarioViewModel @Inject constructor(
    private val repository: PersonaRepository,
    private val personaDao: PersonaDao,
    private val api: PersonaApi,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _estado = MutableStateFlow(UsuarioUiState())
    val estado: StateFlow<UsuarioUiState> = _estado.asStateFlow()

    private val personaId: String = sessionManager.personaId.orEmpty()

    val persona: StateFlow<Persona?> =
        repository.observarPersona(personaId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { refrescar() }

    /**
     * Intenta traer la versión más reciente. Si no hay señal no pasa nada
     * malo: se sigue mostrando lo guardado, que es exactamente para lo que se
     * guardó.
     */
    fun refrescar() {
        if (personaId.isBlank()) return

        viewModelScope.launch {
            _estado.update { it.copy(actualizando = true) }
            try {
                val respuesta = api.obtenerPorId(personaId)
                if (respuesta.isSuccessful) {
                    respuesta.body()?.let { personaDao.guardar(it.toEntity()) }
                    _estado.update { it.copy(actualizando = false, sinConexion = false) }
                } else {
                    _estado.update { it.copy(actualizando = false) }
                }
            } catch (e: Exception) {
                _estado.update { it.copy(actualizando = false, sinConexion = true) }
            }
        }
    }

    fun cerrarSesion(alSalir: () -> Unit) {
        viewModelScope.launch {
            // El rol usuario nunca tiene cola pendiente, así que esto no puede
            // fallar por ese motivo. Se usa el mismo camino igual: la regla de
            // no borrar sobre trabajo sin enviar vive en un solo lugar.
            repository.limpiarDatosLocales()
            sessionManager.cerrarSesion()
            alSalir()
        }
    }
}
