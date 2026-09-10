package com.tuapp.bancopersonas.data.remote

import com.tuapp.bancopersonas.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            // Sin timeouts explícitos, una red móvil lenta deja la
            // sincronización colgada con los valores por defecto.
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            // Sale de BuildConfig: la compilación de depuración apunta al
            // servidor local y la de publicación al de producción.
            // Ver app/build.gradle.kts.
            //
            // Se lee acá dentro y no en una propiedad del módulo porque el
            // procesador de Hilt corre antes de que BuildConfig exista, y una
            // constante a nivel de objeto no le resuelve.
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun providePersonaApi(retrofit: Retrofit): PersonaApi {
        return retrofit.create(PersonaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUsuarioApi(retrofit: Retrofit): UsuarioApi {
        return retrofit.create(UsuarioApi::class.java)
    }

    @Provides
    @Singleton
    fun provideConflictoApi(retrofit: Retrofit): ConflictoApi {
        return retrofit.create(ConflictoApi::class.java)
    }
}
