package com.tuapp.bancopersonas.presentation.usuario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsuarioUiState(
    val persona: Persona? = null,
    val historial: List<HistorialEntity> = emptyList(),
    val isEditing: Boolean = false,
    val isSyncingHistory: Boolean = false,
    val ganadorSemana: Persona? = null,
    val isLoadingGanador: Boolean = true,
    val isOffline: Boolean = false
)

@HiltViewModel
class UsuarioViewModel @Inject constructor(
    private val repository: PersonaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsuarioUiState())
    val uiState: StateFlow<UsuarioUiState> = _uiState

    private var personaId: String? = null

    fun init(id: String) {
        if (personaId == id) return
        personaId = id
        
        viewModelScope.launch {
            // Observar persona
            launch {
                repository.observarPersonas().collect { personas ->
                    val me = personas.find { it.id == id }
                    _uiState.update { it.copy(persona = me) }
                }
            }
            // Observar historial
            launch {
                repository.observarHistorial(id).collect { hist ->
                    _uiState.update { it.copy(historial = hist) }
                }
            }
            
            // Sincronizar historial con backend
            _uiState.update { it.copy(isSyncingHistory = true) }
            repository.sincronizarHistorial(id)
            _uiState.update { it.copy(isSyncingHistory = false) }

            // Cargar ganador semana
            launch {
                val result = repository.getGanadorSemana()
                result.onSuccess { ganador ->
                    _uiState.update { it.copy(ganadorSemana = ganador, isLoadingGanador = false, isOffline = false) }
                }.onFailure {
                    _uiState.update { it.copy(isLoadingGanador = false, isOffline = true) }
                }
            }
        }
    }

    fun setEditing(isEditing: Boolean) {
        _uiState.update { it.copy(isEditing = isEditing) }
    }

    fun guardarCambios(nombre: String, documento: String, telefono: String) {
        val currentPersona = _uiState.value.persona ?: return
        viewModelScope.launch {
            val actualizada = currentPersona.copy(
                nombre = nombre,
                documento = documento,
                telefono = telefono
            )
            repository.editarPersona(actualizada)
            setEditing(false)
            repository.sincronizarHistorial(currentPersona.id) // Refrescar historial
        }
    }

    fun revertirA(historial: HistorialEntity) {
        val currentPersona = _uiState.value.persona ?: return
        viewModelScope.launch {
            repository.revertirHistorial(currentPersona, historial)
            repository.sincronizarHistorial(currentPersona.id) // Refrescar historial
        }
    }
}
