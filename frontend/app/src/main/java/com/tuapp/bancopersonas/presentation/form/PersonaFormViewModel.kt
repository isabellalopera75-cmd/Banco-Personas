package com.tuapp.bancopersonas.presentation.form

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.usecase.CrearPersonaUseCase
import com.tuapp.bancopersonas.domain.usecase.EditarPersonaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonaFormUiState(
    val id: String = "",
    val nombre: String = "",
    val documento: String = "",
    val telefono: String = "",
    val version: Int = 1,
    val esEdicion: Boolean = false
)

@HiltViewModel
class PersonaFormViewModel @Inject constructor(
    private val crearPersonaUseCase: CrearPersonaUseCase,
    private val editarPersonaUseCase: EditarPersonaUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonaFormUiState())
    val uiState: StateFlow<PersonaFormUiState> = _uiState

    fun iniciarFormulario(persona: Persona?) {
        _uiState.value = if (persona != null) {
            PersonaFormUiState(
                id = persona.id,
                nombre = persona.nombre,
                documento = persona.documento,
                telefono = persona.telefono,
                version = persona.version,
                esEdicion = true
            )
        } else {
            PersonaFormUiState()
        }
    }

    fun actualizarNombre(valor: String) {
        _uiState.update { it.copy(nombre = valor) }
    }

    fun actualizarDocumento(valor: String) {
        _uiState.update { it.copy(documento = valor) }
    }

    fun actualizarTelefono(valor: String) {
        _uiState.update { it.copy(telefono = valor) }
    }

    // Ahora recibe directamente qué hacer al terminar, en vez de "avisar" con una bandera
    fun guardar(onExito: () -> Unit) {
        Log.d("SYNC_DEBUG", "ViewModel: Intentando guardar. esEdicion=${_uiState.value.esEdicion}")
        viewModelScope.launch {
            val estado = _uiState.value
            val persona = Persona(
                id = estado.id,
                nombre = estado.nombre,
                documento = estado.documento,
                telefono = estado.telefono,
                version = estado.version,
                syncStatus = SyncStatus.PENDING
            )
            if (estado.esEdicion) {
                editarPersonaUseCase(persona)
            } else {
                crearPersonaUseCase(persona)
            }
            onExito()
        }
    }
}