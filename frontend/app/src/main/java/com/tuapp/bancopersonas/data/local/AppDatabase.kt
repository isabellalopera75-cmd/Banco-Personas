package com.tuapp.bancopersonas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tuapp.bancopersonas.data.local.dao.HistorialDao
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import com.tuapp.bancopersonas.data.local.entity.HistorialEntity
import com.tuapp.bancopersonas.data.local.entity.OutboxEntity
import com.tuapp.bancopersonas.data.local.entity.PersonaEntity

/**
 * Versión 3: el esquema cambió por completo junto con el del servidor.
 *
 * No hay migración desde la versión 2 y es deliberado: las tablas viejas
 * hablaban de otro modelo — un solo campo `nombre`, sin tipo de documento, sin
 * autor — y no hay forma de derivar los campos nuevos, que además son
 * obligatorios. Cualquier migración inventaría datos.
 *
 * Se resuelve con borrado destructivo porque no hay instalaciones reales: el
 * despliegue anterior era de prueba. Si alguna vez hubiera dispositivos en
 * campo con cola pendiente, esto habría que hacerlo al revés — vaciar la cola
 * primero y recién después actualizar.
 */
@Database(
    entities = [PersonaEntity::class, OutboxEntity::class, HistorialEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personaDao(): PersonaDao
    abstract fun outboxDao(): OutboxDao
    abstract fun historialDao(): HistorialDao
}
