package com.tuapp.bancopersonas.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.bancopersonas.data.local.LocalPasswordVerifier
import com.tuapp.bancopersonas.data.local.SessionManager
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.remote.AuthApi
import com.tuapp.bancopersonas.data.remote.dto.LoginOperadorRequest
import com.tuapp.bancopersonas.data.remote.dto.LoginPersonaRequest
import com.tuapp.bancopersonas.domain.model.TipoDocumento
import com.tuapp.bancopersonas.domain.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

enum class ModoLogin { OPERADOR, PERSONA }

data class LoginUiState(
    val modo: ModoLogin = ModoLogin.OPERADOR,
    val usuario: String = "",
    val password: String = "",
    val tipoDocumento: TipoDocumento = TipoDocumento.CC,
    val numeroDocumento: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
    val avisoSinConexion: String? = null,
)

/** Adónde ir después de entrar. */
sealed interface DestinoLogin {
    data class Operador(val rol: String, val sinConexion: Boolean) : DestinoLogin
    data class Persona(val personaId: String, val sinConexion: Boolean) : DestinoLogin
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val personaDao: PersonaDao,
    private val sessionManager: SessionManager,
    private val verificador: LocalPasswordVerifier,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _estado = MutableStateFlow(LoginUiState())
    val estado: StateFlow<LoginUiState> = _estado.asStateFlow()

    private val _destino = MutableStateFlow<DestinoLogin?>(null)
    val destino: StateFlow<DestinoLogin?> = _destino.asStateFlow()

    fun cambiarModo(modo: ModoLogin) =
        _estado.update { it.copy(modo = modo, error = null, avisoSinConexion = null) }

    fun cambiarUsuario(valor: String) = _estado.update { it.copy(usuario = valor, error = null) }
    fun cambiarPassword(valor: String) = _estado.update { it.copy(password = valor, error = null) }
    fun cambiarTipoDocumento(valor: TipoDocumento) = _estado.update { it.copy(tipoDocumento = valor, error = null) }
    fun cambiarNumeroDocumento(valor: String) =
        _estado.update { it.copy(numeroDocumento = valor.filter { c -> c.isDigit() || c.isLetter() }, error = null) }

    fun destinoConsumido() {
        _destino.value = null
    }

    // ======================================================================
    // Operador: admin y registrador
    // ======================================================================
    fun entrarComoOperador() {
        val usuario = _estado.value.usuario.trim()
        val password = _estado.value.password

        if (usuario.isBlank() || password.isBlank()) {
            _estado.update { it.copy(error = "Completá usuario y contraseña") }
            return
        }

        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null, avisoSinConexion = null) }

            try {
                val respuesta = authApi.loginOperador(LoginOperadorRequest(usuario, password))
                val cuerpo = respuesta.body()

                when {
                    respuesta.isSuccessful && cuerpo?.token != null && cuerpo.usuario != null -> {
                        sessionManager.abrirSesionOperador(
                            id = cuerpo.usuario.id,
                            usuario = cuerpo.usuario.usuario,
                            rolNuevo = cuerpo.usuario.rol,
                            tokenNuevo = cuerpo.token,
                        )

                        // Se guarda el verificador recién ahora, con la
                        // contraseña ya confirmada por el servidor. Es lo que
                        // le permite a este registrador volver a entrar en el
                        // campo, sin señal.
                        sessionManager.guardarVerificador(usuario, verificador.derivar(password))
                        sessionManager.guardarPerfilOffline(
                            usuario, cuerpo.usuario.id, cuerpo.usuario.rol
                        )

                        syncScheduler.sincronizarAhora()
                        _estado.update { it.copy(cargando = false, password = "") }
                        _destino.value = DestinoLogin.Operador(cuerpo.usuario.rol, false)
                    }

                    respuesta.code() == 403 -> fallar("La cuenta está desactivada. Hablá con el administrador.")
                    respuesta.code() == 429 -> fallar("Demasiados intentos. Esperá unos minutos.")
                    else -> fallar("Usuario o contraseña incorrectos")
                }
            } catch (e: IOException) {
                entrarSinConexion(usuario, password)
            } catch (e: Exception) {
                fallar("No se pudo iniciar sesión: ${e.message}")
            }
        }
    }

    private fun entrarSinConexion(usuario: String, password: String) {
        val guardado = sessionManager.obtenerVerificador(usuario)
        val perfil = sessionManager.perfilOffline(usuario)

        if (guardado == null || perfil == null) {
            fallar(
                "Sin conexión y este usuario nunca entró en este teléfono. " +
                    "La primera vez hay que iniciar sesión con internet."
            )
            return
        }

        if (!verificador.verificar(password, guardado)) {
            fallar("Usuario o contraseña incorrectos")
            return
        }

        val (id, rol) = perfil

        // El administrador trabaja siempre con conexión: su panel consulta
        // todo contra el servidor. Dejarlo entrar sin señal solo le mostraría
        // pantallas vacías sin explicarle por qué.
        if (rol != SessionManager.ROL_REGISTRADOR) {
            fallar("El panel de administración necesita conexión a internet.")
            return
        }

        sessionManager.abrirSesionOperador(id, usuario, rol, tokenNuevo = null)
        _estado.update { it.copy(cargando = false, password = "") }
        _destino.value = DestinoLogin.Operador(rol, sinConexion = true)
    }

    // ======================================================================
    // Persona del padrón
    // ======================================================================
    fun entrarComoPersona() {
        val tipo = _estado.value.tipoDocumento
        val numero = _estado.value.numeroDocumento.trim()

        if (numero.isBlank()) {
            _estado.update { it.copy(error = "Escribí tu número de documento") }
            return
        }

        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null, avisoSinConexion = null) }

            try {
                val respuesta = authApi.loginPersona(LoginPersonaRequest(tipo.name, numero))
                val cuerpo = respuesta.body()

                when {
                    respuesta.isSuccessful && cuerpo?.persona != null -> {
                        // Se guarda la fila para que la próxima vez pueda
                        // entrar sin señal. Es una sola: la suya.
                        personaDao.guardar(cuerpo.persona.toEntity())
                        sessionManager.abrirSesionPersona(cuerpo.persona.id, cuerpo.token)
                        _estado.update { it.copy(cargando = false) }
                        _destino.value = DestinoLogin.Persona(cuerpo.persona.id, false)
                    }

                    respuesta.code() == 429 -> fallar("Demasiados intentos. Esperá unos minutos.")
                    else -> fallar("No encontramos un registro con ese documento")
                }
            } catch (e: IOException) {
                // Sin conexión se busca en lo que quedó guardado. Como el
                // cierre de sesión borra la base local, lo único que puede
                // haber acá es el registro de quien entró antes en este mismo
                // teléfono: no hay forma de consultar los datos de un tercero.
                val local = personaDao.obtenerPorDocumento(tipo.name, numero)

                if (local == null) {
                    fallar(
                        "Sin conexión y no hay datos guardados en este teléfono. " +
                            "La primera vez hay que entrar con internet."
                    )
                    return@launch
                }

                sessionManager.abrirSesionPersona(local.id, tokenNuevo = null)
                _estado.update { it.copy(cargando = false) }
                _destino.value = DestinoLogin.Persona(local.id, sinConexion = true)
            } catch (e: Exception) {
                fallar("No se pudo iniciar sesión: ${e.message}")
            }
        }
    }

    private fun fallar(mensaje: String) =
        _estado.update { it.copy(cargando = false, error = mensaje) }
}
