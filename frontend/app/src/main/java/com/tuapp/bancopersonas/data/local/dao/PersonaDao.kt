package com.tuapp.bancopersonas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tuapp.bancopersonas.data.local.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas WHERE deletedAt IS NULL ORDER BY nombre")
    fun observarTodas(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun obtenerPorId(id: String): PersonaEntity?

    @Query("SELECT * FROM personas WHERE documento = :documento AND deletedAt IS NULL LIMIT 1")
    suspend fun obtenerPorDocumento(documento: String): PersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(persona: PersonaEntity)

    // Marca la baja como PENDING: si quedara en SYNCED, la fase de descarga
    // volvería a traer el registro del servidor y lo resucitaría antes de que
    // la outbox llegue a enviar el DELETE.
    @Query("UPDATE personas SET deletedAt = :deletedAt, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun marcarComoEliminado(id: String, deletedAt: Long)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun eliminarFisicamente(id: String)

    @Query("UPDATE personas SET syncStatus = :status WHERE id = :id")
    suspend fun actualizarSyncStatus(id: String, status: String)

    @Query("UPDATE personas SET version = :version, syncStatus = :status WHERE id = :id")
    suspend fun actualizarVersionYStatus(id: String, version: Int, status: String)
}
