package com.tuapp.bancopersonas.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.usecase.EliminarPersonaUseCase
import com.tuapp.bancopersonas.domain.usecase.ObtenerPersonasUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonaListUiState(
    val personas: List<Persona> = emptyList(),
    val cargando: Boolean = true
)

@HiltViewModel
class PersonaListViewModel @Inject constructor(
    private val obtenerPersonasUseCase: ObtenerPersonasUseCase,
    private val eliminarPersonaUseCase: EliminarPersonaUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonaListUiState())
    val uiState: StateFlow<PersonaListUiState> = _uiState

    init {
        observarPersonas()
    }

    private fun observarPersonas() {
        viewModelScope.launch {
            obtenerPersonasUseCase().collect { lista ->
                _uiState.update { it.copy(personas = lista, cargando = false) }
            }
        }
    }

    fun eliminarPersona(id: String) {
        viewModelScope.launch {
            eliminarPersonaUseCase(id)
        }
    }
}