package com.tuapp.bancopersonas.presentation.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.data.local.LocalPasswordVerifier
import com.tuapp.bancopersonas.data.local.SessionManager
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.mapper.toDomain
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.data.remote.AuthApi
import com.tuapp.bancopersonas.data.remote.dto.LoginRequest
import com.tuapp.bancopersonas.domain.model.Persona
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val nombre: String = "",
    val documento: String = "",
    val password: String = "",
    val rol: String = "usuario", // "admin" o "usuario"
    val cargando: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val personaDao: PersonaDao,
    private val sessionManager: SessionManager,
    private val passwordVerifier: LocalPasswordVerifier
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onNombreChange(valor: String) = _uiState.update { it.copy(nombre = valor, error = null) }
    fun onDocumentoChange(valor: String) = _uiState.update { it.copy(documento = valor, error = null) }
    fun onPasswordChange(valor: String) = _uiState.update { it.copy(password = valor, error = null) }
    fun onRolChange(valor: String) = _uiState.update { it.copy(rol = valor, error = null) }

    fun login(onSuccess: (String, Persona?) -> Unit) {
        val estado = _uiState.value

        validar(estado)?.let { mensaje ->
            _uiState.update { it.copy(error = mensaje) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }

            try {
                val respuesta = authApi.login(
                    LoginRequest(
                        rol = estado.rol,
                        nombre = if (estado.rol == "admin") estado.nombre else null,
                        documento = if (estado.rol == "usuario") estado.documento else null,
                        password = estado.password
                    )
                )

                val cuerpo = respuesta.body()

                if (respuesta.isSuccessful && cuerpo?.success == true && cuerpo.token != null) {
                    sessionManager.token = cuerpo.token
                    sessionManager.rol = cuerpo.rol
                    sessionManager.personaId = cuerpo.persona?.id

                    // Verificador local: permite volver a entrar sin conexión.
                    // Se deriva de la contraseña que el servidor acaba de dar
                    // por válida, así que no se confía en nada sin verificar.
                    if (estado.rol == "usuario") {
                        sessionManager.guardarVerificador(
                            estado.documento,
                            passwordVerifier.derivar(estado.password)
                        )
                    }

                    val dto = cuerpo.persona
                    if (dto != null) {
                        // No se pisan cambios locales sin subir.
                        val local = personaDao.obtenerPorId(dto.id)
                        if (local == null || local.syncStatus == "SYNCED") {
                            personaDao.guardar(dto.toEntity())
                        }
                    }

                    _uiState.update { it.copy(cargando = false, password = "") }
                    onSuccess(cuerpo.rol ?: estado.rol, dto?.toDomain())
                } else {
                    _uiState.update {
                        it.copy(
                            error = cuerpo?.error ?: "Documento o contraseña incorrectos",
                            cargando = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.d("SYNC_DEBUG", "Login sin conexión: ${e.message}")
                intentarLoginSinConexion(estado, onSuccess)
            }
        }
    }

    private fun validar(estado: LoginUiState): String? = when {
        estado.password.isBlank() -> "Ingresá tu contraseña"
        estado.rol == "admin" && estado.nombre.isBlank() -> "Ingresá el usuario de administrador"
        estado.rol == "usuario" && estado.documento.isBlank() -> "Ingresá tu documento"
        else -> null
    }

    /**
     * Entrada sin conexión.
     *
     * Solo funciona para participantes que ya iniciaron sesión antes en este
     * dispositivo: la contraseña se valida contra el verificador local que se
     * derivó en ese momento.
     *
     * El acceso de administración queda deliberadamente afuera. Las
     * credenciales de admin ya no viven dentro del APK, y no van a volver:
     * cualquiera puede descargar la aplicación y leer lo que tenga adentro.
     */
    private suspend fun intentarLoginSinConexion(
        estado: LoginUiState,
        onSuccess: (String, Persona?) -> Unit
    ) {
        if (estado.rol != "usuario") {
            _uiState.update {
                it.copy(
                    error = "El acceso de administrador necesita conexión a internet.",
                    cargando = false
                )
            }
            return
        }

        val verificador = sessionManager.obtenerVerificador(estado.documento)
        val persona = personaDao.obtenerPorDocumento(estado.documento)

        val credencialValida = verificador != null &&
            persona != null &&
            passwordVerifier.verificar(estado.password, verificador)

        if (!credencialValida) {
            _uiState.update {
                it.copy(
                    error = "No se pudo conectar al servidor. Sin internet solo " +
                        "podés entrar si ya iniciaste sesión antes en este dispositivo.",
                    cargando = false
                )
            }
            return
        }

        _uiState.update { it.copy(cargando = false, password = "") }
        onSuccess("usuario", persona!!.toDomain())
    }
}
