package com.tuapp.bancopersonas.presentation.conflictos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.data.remote.ConflictoApi
import com.tuapp.bancopersonas.data.remote.dto.ConflictoAdminDto
import com.tuapp.bancopersonas.data.remote.dto.CrearComoNuevaRequest
import com.tuapp.bancopersonas.data.remote.dto.DescartarRequest
import com.tuapp.bancopersonas.data.remote.dto.FusionarRequest
import com.tuapp.bancopersonas.data.remote.dto.PersonaDto
import com.tuapp.bancopersonas.domain.model.CamposPersona
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Un campo enfrentado: lo que hay guardado contra lo que llegó. */
data class CampoEnfrentado(
    val campo: String,
    val etiqueta: String,
    val valorActual: String?,
    val valorEnviado: String?,
    val elegido: Boolean,
) {
    val hayDiferencia: Boolean get() = normalizar(valorActual) != normalizar(valorEnviado)

    private fun normalizar(v: String?) = v?.trim()?.takeIf { it.isNotEmpty() }
}

data class ConflictosUiState(
    val cargando: Boolean = true,
    val conflictos: List<ConflictoAdminDto> = emptyList(),
    val seleccionado: ConflictoAdminDto? = null,
    val campos: List<CampoEnfrentado> = emptyList(),
    val error: String? = null,
    val mensaje: String? = null,
    val resolviendo: Boolean = false,
)

@HiltViewModel
class ConflictosViewModel @Inject constructor(
    private val api: ConflictoApi,
) : ViewModel() {

    private val _estado = MutableStateFlow(ConflictosUiState())
    val estado: StateFlow<ConflictosUiState> = _estado.asStateFlow()

    init { cargar() }

    fun cargar() {
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            try {
                val respuesta = api.listar()
                if (respuesta.isSuccessful) {
                    _estado.update {
                        it.copy(
                            cargando = false,
                            conflictos = respuesta.body()?.conflictos.orEmpty(),
                        )
                    }
                } else {
                    fallar("No se pudo leer la cola (${respuesta.code()})")
                }
            } catch (e: Exception) {
                // Esta pantalla es del admin, que trabaja siempre con
                // conexión. Sin señal no hay nada que mostrar, y decirlo es
                // mejor que una lista vacía que parece «no hay conflictos».
                fallar("Sin conexión. La cola de conflictos se consulta en línea.")
            }
        }
    }

    /**
     * Prepara la comparación campo por campo.
     *
     * Por defecto NO se elige nada: lo que ya está guardado queda como está.
     * Es la opción conservadora — el admin marca solo lo que quiere traer del
     * intento, en vez de tener que desmarcar lo que no.
     */
    fun seleccionar(conflicto: ConflictoAdminDto) {
        val actual = conflicto.personaExistente
        val enviado = conflicto.datosEnviados

        val campos = ORDEN_CAMPOS.map { campo ->
            CampoEnfrentado(
                campo = campo,
                etiqueta = CamposPersona.etiqueta(campo),
                valorActual = valorDe(actual, campo),
                valorEnviado = enviado[campo],
                elegido = false,
            )
        }

        _estado.update { it.copy(seleccionado = conflicto, campos = campos) }
    }

    fun cerrarDetalle() =
        _estado.update { it.copy(seleccionado = null, campos = emptyList()) }

    fun alternarCampo(campo: String) = _estado.update { estado ->
        estado.copy(
            campos = estado.campos.map {
                if (it.campo == campo) it.copy(elegido = !it.elegido) else it
            }
        )
    }

    fun fusionar(nota: String) {
        val conflicto = _estado.value.seleccionado ?: return
        val elegidos = _estado.value.campos
            .filter { it.elegido }
            .associate { it.campo to it.valorEnviado }

        ejecutar { api.fusionar(conflicto.id, FusionarRequest(elegidos, nota.ifBlank { null })) }
    }

    fun crearComoNueva(tipoDocumento: String, numeroDocumento: String) {
        val conflicto = _estado.value.seleccionado ?: return
        ejecutar {
            api.crearComoNueva(
                conflicto.id,
                CrearComoNuevaRequest(tipoDocumento, numeroDocumento)
            )
        }
    }

    fun descartar(nota: String) {
        val conflicto = _estado.value.seleccionado ?: return

        if (nota.trim().length < 5) {
            _estado.update { it.copy(error = "Explicá por qué se descarta.") }
            return
        }

        ejecutar { api.descartar(conflicto.id, DescartarRequest(nota.trim())) }
    }

    private fun ejecutar(accion: suspend () -> retrofit2.Response<Map<String, Any>>) {
        viewModelScope.launch {
            _estado.update { it.copy(resolviendo = true, error = null) }
            try {
                val respuesta = accion()
                if (respuesta.isSuccessful) {
                    _estado.update {
                        it.copy(
                            resolviendo = false,
                            seleccionado = null,
                            campos = emptyList(),
                            mensaje = "Conflicto resuelto",
                        )
                    }
                    cargar()
                } else {
                    _estado.update {
                        it.copy(resolviendo = false, error = "El servidor rechazó la resolución (${respuesta.code()})")
                    }
                }
            } catch (e: Exception) {
                _estado.update { it.copy(resolviendo = false, error = "No se pudo resolver: ${e.message}") }
            }
        }
    }

    fun mensajeConsumido() = _estado.update { it.copy(mensaje = null, error = null) }

    private fun fallar(mensaje: String) =
        _estado.update { it.copy(cargando = false, error = mensaje) }

    private fun valorDe(dto: PersonaDto?, campo: String): String? = when (campo) {
        CamposPersona.TIPO_DOCUMENTO -> dto?.tipoDocumento
        CamposPersona.NUMERO_DOCUMENTO -> dto?.numeroDocumento
        CamposPersona.PRIMER_NOMBRE -> dto?.primerNombre
        CamposPersona.SEGUNDO_NOMBRE -> dto?.segundoNombre
        CamposPersona.PRIMER_APELLIDO -> dto?.primerApellido
        CamposPersona.SEGUNDO_APELLIDO -> dto?.segundoApellido
        CamposPersona.FECHA_NACIMIENTO -> dto?.fechaNacimiento
        CamposPersona.SEXO -> dto?.sexo
        CamposPersona.TELEFONO -> dto?.telefono
        else -> null
    }

    private companion object {
        val ORDEN_CAMPOS = listOf(
            CamposPersona.TIPO_DOCUMENTO,
            CamposPersona.NUMERO_DOCUMENTO,
            CamposPersona.PRIMER_NOMBRE,
            CamposPersona.SEGUNDO_NOMBRE,
            CamposPersona.PRIMER_APELLIDO,
            CamposPersona.SEGUNDO_APELLIDO,
            CamposPersona.FECHA_NACIMIENTO,
            CamposPersona.SEXO,
            CamposPersona.TELEFONO,
        )
    }
}
