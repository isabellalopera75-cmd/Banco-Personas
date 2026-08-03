package com.tuapp.bancopersonas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tuapp.bancopersonas.data.local.entity.OutboxEntity

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun encolar(outbox: OutboxEntity)

    @Query("SELECT * FROM outbox ORDER BY creadoEn ASC")
    suspend fun obtenerPendientes(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE outboxId = :outboxId")
    suspend fun eliminar(outboxId: Long)

    @Query("UPDATE outbox SET intentos = intentos + 1 WHERE outboxId = :outboxId")
    suspend fun incrementarIntentos(outboxId: Long)

    @Query("UPDATE outbox SET payload = :nuevoPayload WHERE outboxId = :outboxId")
    suspend fun actualizarPayload(outboxId: Long, nuevoPayload: String)
}
