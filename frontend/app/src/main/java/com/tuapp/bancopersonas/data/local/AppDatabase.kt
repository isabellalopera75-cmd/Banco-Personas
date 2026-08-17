package com.tuapp.bancopersonas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tuapp.bancopersonas.data.local.dao.HistorialDao
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.data.local.entity.OutboxEntity
import com.tuapp.bancopersonas.data.local.entity.PersonaEntity

@Database(
    entities = [PersonaEntity::class, OutboxEntity::class, HistorialEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao
    abstract fun outboxDao(): OutboxDao
    abstract fun historialDao(): HistorialDao
}