package com.tuapp.bancopersonas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tuapp.bancopersonas.data.local.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {

    @Query(
        """SELECT * FROM personas WHERE deletedAt IS NULL
           ORDER BY primerApellido, segundoApellido, primerNombre"""
    )
    fun observarTodas(): Flow<List<PersonaEntity>>

    /** El rol usuario observa una sola fila: la suya. */
    @Query("SELECT * FROM personas WHERE id = :id AND deletedAt IS NULL")
    fun observarUna(id: String): Flow<PersonaEntity?>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun obtenerPorId(id: String): PersonaEntity?

    @Query(
        """SELECT * FROM personas
           WHERE tipoDocumento = :tipo AND numeroDocumento = :numero
             AND deletedAt IS NULL LIMIT 1"""
    )
    suspend fun obtenerPorDocumento(tipo: String, numero: String): PersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(persona: PersonaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarVarias(personas: List<PersonaEntity>)

    // Se marca PENDING: si quedara en SYNCED, la fase de descarga volvería a
    // traer el registro del servidor y lo resucitaría antes de que la cola
    // llegue a enviar la baja.
    @Query("UPDATE personas SET deletedAt = :deletedAt, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun marcarComoEliminado(id: String, deletedAt: Long)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun eliminarFisicamente(id: String)

    @Query("UPDATE personas SET syncStatus = :status WHERE id = :id")
    suspend fun actualizarSyncStatus(id: String, status: String)

    /**
     * El servidor recibió el cambio pero no lo pudo aplicar y lo guardó como
     * conflicto. El dato está a salvo del otro lado; acá solo queda la marca
     * para que el registrador sepa que ese registro espera una decisión.
     */
    @Query("UPDATE personas SET syncStatus = 'EN_REVISION', conflictoId = :conflictoId WHERE id = :id")
    suspend fun marcarEnRevision(id: String, conflictoId: String?)

    @Query("UPDATE personas SET syncStatus = 'SYNCED', conflictoId = NULL WHERE id = :id")
    suspend fun marcarResuelta(id: String)

    /** Cursor de sincronización: hasta dónde llegó este dispositivo. */
    @Query("SELECT COALESCE(MAX(cambioSeq), 0) FROM personas")
    suspend fun cursorMaximo(): Long

    @Query("SELECT * FROM personas WHERE syncStatus = 'EN_REVISION'")
    suspend fun enRevision(): List<PersonaEntity>

    @Query("DELETE FROM personas")
    suspend fun limpiarTodo()
}
