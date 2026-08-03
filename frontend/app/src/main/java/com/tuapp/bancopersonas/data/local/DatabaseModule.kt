package com.tuapp.bancopersonas.data.local

import android.content.Context
import androidx.room.Room
import com.tuapp.bancopersonas.data.local.dao.OutboxDao
import com.tuapp.bancopersonas.data.local.dao.PersonaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "banco_personas.db"
        ).build()
    }

    @Provides
    fun providePersonaDao(database: AppDatabase): PersonaDao {
        return database.personaDao()
    }

    @Provides
    fun provideOutboxDao(database: AppDatabase): OutboxDao {
        return database.outboxDao()
    }
}