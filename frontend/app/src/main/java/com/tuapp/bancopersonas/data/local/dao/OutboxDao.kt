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

    @Query("SELECT * FROM outbox ORDER BY creadoEn ASC, outboxId ASC")
    suspend fun obtenerPendientes(): List<OutboxEntity>

    /**
     * Lo que ya está encolado para esta persona y operación.
     *
     * Se consulta antes de encolar para unificar. Dos UPDATE sueltos de la
     * misma persona viajarían con la misma version_base, y el segundo entraría
     * en conflicto contra el primero: el registrador vería su propio cambio
     * rechazado por sí mismo.
     */
    @Query("SELECT * FROM outbox WHERE personaId = :personaId AND operacion = :operacion LIMIT 1")
    suspend fun pendientePara(personaId: String, operacion: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE personaId = :personaId")
    suspend fun pendientesDe(personaId: String): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE outboxId = :outboxId")
    suspend fun eliminar(outboxId: Long)

    @Query("DELETE FROM outbox WHERE personaId = :personaId")
    suspend fun eliminarDe(personaId: String)

    @Query("UPDATE outbox SET intentos = intentos + 1 WHERE outboxId = :outboxId")
    suspend fun incrementarIntentos(outboxId: Long)

    @Query("UPDATE outbox SET payload = :nuevoPayload WHERE outboxId = :outboxId")
    suspend fun actualizarPayload(outboxId: Long, nuevoPayload: String)

    /** Cuántas operaciones quedan sin enviar. Bloquea el cierre de sesión. */
    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun contarPendientes(): Int

    @Query("DELETE FROM outbox")
    suspend fun limpiarTodo()
}
