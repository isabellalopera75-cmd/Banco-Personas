package com.tuapp.bancopersonas.data.repository

import com.tuapp.bancopersonas.domain.repository.PersonaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPersonaRepository(
        impl: PersonaRepositoryImpl
    ): PersonaRepository
}