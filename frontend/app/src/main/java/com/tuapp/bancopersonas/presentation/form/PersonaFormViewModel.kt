package com.tuapp.bancopersonas.presentation.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.usecase.CrearPersonaUseCase
import com.tuapp.bancopersonas.domain.usecase.EditarPersonaUseCase
import com.tuapp.bancopersonas.domain.usecase.ResultadoGuardado
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
    val password: String = "",
    val confirmacion: String = "",
    val version: Int = 1,
    val esEdicion: Boolean = false,
    val error: String? = null
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

    fun actualizarNombre(valor: String) = _uiState.update { it.copy(nombre = valor, error = null) }
    fun actualizarDocumento(valor: String) = _uiState.update { it.copy(documento = valor, error = null) }
    fun actualizarTelefono(valor: String) = _uiState.update { it.copy(telefono = valor, error = null) }
    fun actualizarPassword(valor: String) = _uiState.update { it.copy(password = valor, error = null) }
    fun actualizarConfirmacion(valor: String) = _uiState.update { it.copy(confirmacion = valor, error = null) }

    fun guardar(onExito: () -> Unit) {
        val estado = _uiState.value

        // El alta crea una cuenta, así que valida contraseña. La edición no
        // toca credenciales, por eso solo se controla al registrar.
        if (!estado.esEdicion) {
            validarPassword(estado)?.let { mensaje ->
                _uiState.update { it.copy(error = mensaje) }
                return
            }
        }

        viewModelScope.launch {
            val persona = Persona(
                id = estado.id,
                nombre = estado.nombre.trim(),
                documento = estado.documento.trim(),
                telefono = estado.telefono.trim(),
                version = estado.version,
                syncStatus = SyncStatus.PENDING
            )

            val resultado = if (estado.esEdicion) {
                editarPersonaUseCase(persona)
            } else {
                crearPersonaUseCase(persona, estado.password)
            }

            when (resultado) {
                ResultadoGuardado.Exito -> onExito()

                // Antes esto no existía: el formulario cerraba como si todo
                // hubiera salido bien y el rechazo aparecía —o no aparecía—
                // mucho después, en la sincronización.
                ResultadoGuardado.DocumentoDuplicado -> _uiState.update {
                    it.copy(error = "Ese documento ya pertenece a otro participante")
                }
            }
        }
    }

    private fun validarPassword(estado: PersonaFormUiState): String? = when {
        // El mismo mínimo que valida el servidor: si difieren, el usuario
        // recibiría un rechazo recién al sincronizar, sin entender por qué.
        estado.password.length < LARGO_MINIMO_PASSWORD ->
            "La contraseña debe tener al menos $LARGO_MINIMO_PASSWORD caracteres"
        estado.password != estado.confirmacion ->
            "Las contraseñas no coinciden"
        else -> null
    }

    private companion object {
        const val LARGO_MINIMO_PASSWORD = 8
    }
}
