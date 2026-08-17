package com.tuapp.bancopersonas.presentation.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.data.mapper.toDomain
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.data.remote.AuthApi
import com.tuapp.bancopersonas.data.remote.dto.LoginRequest
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
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
    private val personaDao: PersonaDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onNombreChange(valor: String) = _uiState.update { it.copy(nombre = valor, error = null) }
    fun onDocumentoChange(valor: String) = _uiState.update { it.copy(documento = valor, error = null) }
    fun onPasswordChange(valor: String) = _uiState.update { it.copy(password = valor, error = null) }
    fun onRolChange(valor: String) = _uiState.update { it.copy(rol = valor, error = null) }

    fun login(onSuccess: (String, Persona?) -> Unit) {
        viewModelScope.launch {
            val estado = _uiState.value
            _uiState.update { it.copy(cargando = true, error = null) }

            try {
                val request = LoginRequest(
                    rol = estado.rol,
                    nombre = estado.nombre,
                    documento = if (estado.rol == "usuario") estado.documento else null,
                    password = if (estado.rol == "admin") estado.password else null
                )

                val response = authApi.login(request)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    val persona = body.persona?.toDomain()
                    if (persona != null) {
                        // Protegemos la integridad: si hay cambios locales PENDING, no sobrescribimos
                        // con la versión del servidor (que es más antigua que el cambio pendiente).
                        val local = personaDao.obtenerPorId(persona.id)
                        if (local == null || local.syncStatus == "SYNCED") {
                            personaDao.guardar(persona.toEntity())
                        } else {
                            Log.d("SYNC_DEBUG", "Login: Respetando datos locales pendientes para ${persona.nombre}")
                        }
                    }
                    onSuccess(body.rol ?: estado.rol, persona)
                } else {
                    val errorMsg = response.body()?.error ?: "Error al iniciar sesión"
                    _uiState.update { it.copy(error = errorMsg, cargando = false) }
                }
            } catch (e: Exception) {
                // Fallback offline
                if (estado.rol == "usuario") {
                    val personaOffline = personaDao.loginOffline(estado.nombre, estado.documento)
                    if (personaOffline != null) {
                        onSuccess("usuario", personaOffline.toDomain())
                        return@launch
                    }
                } else if (estado.rol == "admin") {
                    // Fallback para Admin offline con credenciales fijas
                    if (estado.nombre == "admin" && estado.password == "admin123") {
                        onSuccess("admin", null)
                        return@launch
                    }
                }
                _uiState.update { it.copy(error = "No se pudo conectar al servidor. Verifica tu conexión o intenta nuevamente.", cargando = false) }
            }
        }
    }
}