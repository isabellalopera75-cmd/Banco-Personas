package com.tuapp.bancopersonas.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.tuapp.bancopersonas.data.local.AppDatabase
import com.tuapp.bancopersonas.data.mapper.toEntity
import com.tuapp.bancopersonas.data.remote.PersonaApi
import com.tuapp.bancopersonas.data.remote.dto.EdicionPersonaDto
import com.tuapp.bancopersonas.domain.model.Persona
import com.tuapp.bancopersonas.domain.model.Sexo
import com.tuapp.bancopersonas.domain.model.SyncStatus
import com.tuapp.bancopersonas.domain.model.TipoDocumento
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * La cola de salida contra una base Room de verdad, en un dispositivo.
 *
 * Es lo más delicado que hay del lado del cliente. Un error acá no se ve: la
 * pantalla muestra el valor nuevo igual, porque la fila local ya lo tiene, y
 * el dato simplemente nunca llega al servidor o llega mal.
 *
 * No se toca la red: el `PersonaApi` está sin usar a propósito, porque lo que
 * se verifica es qué QUEDA ENCOLADO, no qué pasa al enviarlo.
 */
@RunWith(AndroidJUnit4::class)
class ColaDeSalidaTest {

    private lateinit var db: AppDatabase
    private lateinit var repositorio: PersonaRepositoryImpl
    private val gson = Gson()

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext

        // En memoria: cada prueba arranca de cero y no deja rastro en el
        // dispositivo. Que Room la construya ya verifica el esquema nuevo.
        db = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val api = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PersonaApi::class.java)

        repositorio = PersonaRepositoryImpl(
            personaDao = db.personaDao(),
            outboxDao = db.outboxDao(),
            historialDao = db.historialDao(),
            api = api,
        )
    }

    @After
    fun cerrar() = db.close()

    private fun rosa(version: Int = 1, telefono: String? = null) = Persona(
        id = "11111111-1111-4111-8111-111111111111",
        tipoDocumento = TipoDocumento.CC,
        numeroDocumento = "1234567",
        primerNombre = "Rosa",
        primerApellido = "Perez",
        fechaNacimiento = "1960-05-01",
        sexo = Sexo.F,
        telefono = telefono,
        version = version,
        syncStatus = SyncStatus.PENDING,
    )

    /** Simula una persona ya descargada del servidor. */
    private suspend fun yaSincronizada(version: Int = 3) {
        db.personaDao().guardar(
            rosa(version = version).toEntity().copy(syncStatus = SyncStatus.SYNCED.name)
        )
    }

    private suspend fun edicionEncolada(): EdicionPersonaDto {
        val pendientes = db.outboxDao().obtenerPendientes()
        assertEquals("debería haber una sola operación encolada", 1, pendientes.size)
        return gson.fromJson(pendientes.first().payload, EdicionPersonaDto::class.java)
    }

    // ======================================================================

    @Test
    fun el_alta_encola_una_operacion_y_guarda_la_fila() = runBlocking {
        repositorio.crearPersona(rosa())

        val cola = db.outboxDao().obtenerPendientes()
        assertEquals(1, cola.size)
        assertEquals("CREATE", cola.first().operacion)
        assertEquals(SyncStatus.PENDING.name, db.personaDao().obtenerPorId(rosa().id)?.syncStatus)
    }

    @Test
    fun editar_manda_solo_los_campos_que_cambiaron() = runBlocking {
        yaSincronizada(version = 3)

        repositorio.editarPersona(rosa(version = 3, telefono = "3001112233"))

        val edicion = edicionEncolada()
        assertEquals(
            "solo el teléfono debería viajar, no el registro entero",
            setOf("telefono"),
            edicion.cambios.keys,
        )
        assertEquals(3, edicion.versionBase)
    }

    @Test
    fun editar_dos_veces_deja_UNA_operacion_con_los_dos_cambios() = runBlocking {
        // Este es el caso que justifica la coalescencia. Con dos operaciones
        // sueltas, ambas viajarían con version_base 3: el servidor aplicaría
        // la primera y vería la segunda como conflicto contra la primera. El
        // registrador vería su propio trabajo rechazado por sí mismo.
        yaSincronizada(version = 3)

        repositorio.editarPersona(rosa(version = 3, telefono = "3001112233"))
        val despuesDelPrimero = repositorio.obtenerPorId(rosa().id)!!
        repositorio.editarPersona(despuesDelPrimero.copy(primerNombre = "Rosalia"))

        val edicion = edicionEncolada()
        assertEquals(setOf("telefono", "primer_nombre"), edicion.cambios.keys)
        assertEquals("3001112233", edicion.cambios["telefono"])
        assertEquals("Rosalia", edicion.cambios["primer_nombre"])
        assertEquals("la versión base tiene que ser la del servidor, no una inventada", 3, edicion.versionBase)
    }

    @Test
    fun la_version_local_no_se_incrementa_al_editar() = runBlocking {
        // El código anterior hacía version + 1 en el teléfono: el cliente
        // inventaba versiones que el servidor nunca había emitido.
        yaSincronizada(version = 3)

        repositorio.editarPersona(rosa(version = 3, telefono = "3001112233"))

        assertEquals(3, db.personaDao().obtenerPorId(rosa().id)?.version)
    }

    @Test
    fun editar_un_alta_sin_enviar_corrige_el_alta_y_no_encola_un_UPDATE() = runBlocking {
        // Para el servidor esa persona todavía no existe: un UPDATE daría 404.
        repositorio.crearPersona(rosa())
        repositorio.editarPersona(rosa(telefono = "3001112233"))

        val cola = db.outboxDao().obtenerPendientes()
        assertEquals(1, cola.size)
        assertEquals("CREATE", cola.first().operacion)
        assertTrue(
            "el teléfono nuevo debería estar dentro del alta",
            cola.first().payload.contains("3001112233"),
        )
    }

    @Test
    fun un_cambio_que_no_cambia_nada_no_encola_nada() = runBlocking {
        yaSincronizada(version = 3)

        repositorio.editarPersona(rosa(version = 3))

        assertTrue(db.outboxDao().obtenerPendientes().isEmpty())
    }

    @Test
    fun dar_de_baja_un_alta_sin_enviar_la_borra_sin_avisarle_al_servidor() = runBlocking {
        repositorio.crearPersona(rosa())

        repositorio.eliminarPersona(rosa().id)

        assertTrue("no hay nada que avisar", db.outboxDao().obtenerPendientes().isEmpty())
        assertNull("la fila local desaparece", db.personaDao().obtenerPorId(rosa().id))
    }

    @Test
    fun dar_de_baja_algo_sincronizado_encola_un_DELETE_con_su_version() = runBlocking {
        yaSincronizada(version = 3)

        repositorio.eliminarPersona(rosa().id)

        val cola = db.outboxDao().obtenerPendientes()
        assertEquals(1, cola.size)
        assertEquals("DELETE", cola.first().operacion)
        assertTrue(cola.first().payload.contains("\"version_base\":3"))
        assertTrue(
            "la fila queda marcada, no se borra: el servidor todavía no lo sabe",
            db.personaDao().obtenerPorId(rosa().id)?.deletedAt != null,
        )
    }

    @Test
    fun dar_de_baja_descarta_las_ediciones_que_esperaban() = runBlocking {
        yaSincronizada(version = 3)
        repositorio.editarPersona(rosa(version = 3, telefono = "3001112233"))

        repositorio.eliminarPersona(rosa().id)

        val cola = db.outboxDao().obtenerPendientes()
        assertEquals("una edición sobre algo que se va de baja no tiene sentido", 1, cola.size)
        assertEquals("DELETE", cola.first().operacion)
    }

    @Test
    fun no_se_puede_borrar_la_base_local_con_la_cola_llena() = runBlocking {
        // Es la regla que protege el trabajo de campo al cerrar sesión.
        repositorio.crearPersona(rosa())

        val resultado = repositorio.limpiarDatosLocales()

        assertTrue(resultado.isFailure)
        assertTrue(db.personaDao().obtenerPorId(rosa().id) != null)
    }

    @Test
    fun con_la_cola_vacia_si_se_borra_todo() = runBlocking {
        yaSincronizada(version = 3)

        val resultado = repositorio.limpiarDatosLocales()

        assertTrue(resultado.isSuccess)
        assertNull(db.personaDao().obtenerPorId(rosa().id))
    }

    @Test
    fun el_cursor_de_sincronizacion_sale_de_la_fila_mas_avanzada() = runBlocking {
        db.personaDao().guardar(
            rosa().toEntityConCursor(10).copy(syncStatus = SyncStatus.SYNCED.name)
        )
        db.personaDao().guardar(
            rosa().copy(id = "22222222-2222-4222-8222-222222222222")
                .toEntityConCursor(42).copy(syncStatus = SyncStatus.SYNCED.name)
        )

        assertEquals(42L, db.personaDao().cursorMaximo())
    }

    private fun Persona.toEntityConCursor(seq: Long) = toEntity(cambioSeq = seq)
}
