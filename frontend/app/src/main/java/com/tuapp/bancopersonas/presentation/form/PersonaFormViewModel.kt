package com.tuapp.bancopersonas.presentation.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.Sexo
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.model.TipoDocumento
import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import com.tuapp.bancopersonas.domain.usecase.CrearPersonaUseCase
import com.tuapp.bancopersonas.domain.usecase.EditarPersonaUseCase
import com.tuapp.bancopersonas.domain.usecase.ResultadoGuardado
import com.tuapp.bancopersonas.ui.components.digitosAFechaIso
import com.tuapp.bancopersonas.ui.components.fechaIsoADigitos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class FormUiState(
    val id: String = "",
    val tipoDocumento: TipoDocumento = TipoDocumento.CC,
    val numeroDocumento: String = "",
    val primerNombre: String = "",
    val segundoNombre: String = "",
    val primerApellido: String = "",
    val segundoApellido: String = "",
    /** Solo dígitos, en orden DDMMAAAA. Las barras se agregan en pantalla. */
    val fechaNacimiento: String = "",
    val sexo: Sexo = Sexo.F,
    val telefono: String = "",
    val esEdicion: Boolean = false,
    val guardando: Boolean = false,
    val error: String? = null,
    val guardado: Boolean = false,
)

@HiltViewModel
class PersonaFormViewModel @Inject constructor(
    private val crearPersona: CrearPersonaUseCase,
    private val editarPersona: EditarPersonaUseCase,
    private val repository: PersonaRepository,
) : ViewModel() {

    private val _estado = MutableStateFlow(FormUiState())
    val estado: StateFlow<FormUiState> = _estado.asStateFlow()

    /** Versión del servidor de la persona en edición. No se toca desde acá. */
    private var versionConocida: Int = 1
    private var creadoPor: String? = null

    fun cargar(personaId: String?) {
        if (personaId == null) {
            _estado.value = FormUiState(id = UUID.randomUUID().toString())
            return
        }

        viewModelScope.launch {
            val persona = repository.obtenerPorId(personaId) ?: return@launch
            versionConocida = persona.version
            creadoPor = persona.creadoPor

            _estado.value = FormUiState(
                id = persona.id,
                tipoDocumento = persona.tipoDocumento,
                numeroDocumento = persona.numeroDocumento,
                primerNombre = persona.primerNombre,
                segundoNombre = persona.segundoNombre.orEmpty(),
                primerApellido = persona.primerApellido,
                segundoApellido = persona.segundoApellido.orEmpty(),
                fechaNacimiento = fechaIsoADigitos(persona.fechaNacimiento),
                sexo = persona.sexo,
                telefono = persona.telefono.orEmpty(),
                esEdicion = true,
            )
        }
    }

    fun cambiarTipoDocumento(v: TipoDocumento) = _estado.update { it.copy(tipoDocumento = v, error = null) }
    fun cambiarNumeroDocumento(v: String) = _estado.update { it.copy(numeroDocumento = v.trim(), error = null) }
    fun cambiarPrimerNombre(v: String) = _estado.update { it.copy(primerNombre = v, error = null) }
    fun cambiarSegundoNombre(v: String) = _estado.update { it.copy(segundoNombre = v, error = null) }
    fun cambiarPrimerApellido(v: String) = _estado.update { it.copy(primerApellido = v, error = null) }
    fun cambiarSegundoApellido(v: String) = _estado.update { it.copy(segundoApellido = v, error = null) }
    fun cambiarSexo(v: Sexo) = _estado.update { it.copy(sexo = v, error = null) }
    fun cambiarTelefono(v: String) = _estado.update { it.copy(telefono = v.filter { c -> c.isDigit() }, error = null) }

    /**
     * Solo se guardan dígitos, hasta ocho. Filtrar acá y no en la pantalla
     * significa que da igual si alguien pega "08/07/2008" desde otro lado:
     * las barras se descartan y queda el mismo valor.
     */
    fun cambiarFechaNacimiento(v: String) = _estado.update {
        it.copy(fechaNacimiento = v.filter { c -> c.isDigit() }.take(8), error = null)
    }

    fun guardar() {
        val e = _estado.value

        // Se valida que la fecha EXISTA, no solo que tenga ocho dígitos. Un
        // 31 de febrero es un dato malo que después nadie audita.
        val fechaIso = digitosAFechaIso(e.fechaNacimiento)
        if (fechaIso == null) {
            _estado.update {
                it.copy(error = "Revisá la fecha de nacimiento. Va como día, mes y año: 08072008 es el 8 de julio de 2008.")
            }
            return
        }

        val persona = Persona(
            id = e.id,
            tipoDocumento = e.tipoDocumento,
            numeroDocumento = e.numeroDocumento.trim(),
            primerNombre = e.primerNombre.trim(),
            segundoNombre = e.segundoNombre.trim().ifBlank { null },
            primerApellido = e.primerApellido.trim(),
            segundoApellido = e.segundoApellido.trim().ifBlank { null },
            fechaNacimiento = fechaIso,
            sexo = e.sexo,
            telefono = e.telefono.trim().ifBlank { null },
            creadoPor = creadoPor,
            version = versionConocida,
            syncStatus = SyncStatus.PENDING,
        )

        viewModelScope.launch {
            _estado.update { it.copy(guardando = true, error = null) }

            val resultado =
                if (e.esEdicion) editarPersona(persona) else crearPersona(persona)

            when (resultado) {
                is ResultadoGuardado.Exito ->
                    _estado.update { it.copy(guardando = false, guardado = true) }

                is ResultadoGuardado.DocumentoDuplicado ->
                    _estado.update {
                        it.copy(
                            guardando = false,
                            // Se nombra a quién pertenece: sin eso, el
                            // registrador no sabe si se equivocó de dígito o si
                            // ya la había registrado antes.
                            error = "Ese documento ya está registrado a nombre de " +
                                "${resultado.nombreExistente}.",
                        )
                    }

                is ResultadoGuardado.Incompleto ->
                    _estado.update {
                        it.copy(
                            guardando = false,
                            error = "Faltan datos: ${resultado.campos.joinToString(", ")}",
                        )
                    }
            }
        }
    }
}
