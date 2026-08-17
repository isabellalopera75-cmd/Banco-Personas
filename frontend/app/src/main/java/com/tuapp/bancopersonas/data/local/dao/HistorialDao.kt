package com.tuapp.bancopersonas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistorialDao {
    @Query("SELECT * FROM historial_cambios WHERE personaId = :personaId ORDER BY creadoEn DESC")
    fun observarHistorial(personaId: String): Flow<List<HistorialEntity>>

    @Query("SELECT * FROM historial_cambios WHERE personaId = :personaId ORDER BY creadoEn DESC")
    suspend fun obtenerHistorial(personaId: String): List<HistorialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarVarios(historiales: List<HistorialEntity>)

    @Query("DELETE FROM historial_cambios WHERE personaId = :personaId")
    suspend fun limpiarHistorial(personaId: String)
}
