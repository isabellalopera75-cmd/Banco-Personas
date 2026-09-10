plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.tuapp.bancopersonas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tuapp.bancopersonas"
        minSdk = 26
        targetSdk = 35
        // 2.0: el esquema cambió por completo. La base local se recrea sola
        // (migración destructiva de Room), pero conviene que la versión lo diga.
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // La URL del servidor deja de estar clavada en el código.
        //
        // Con una sola constante era imposible probar contra un backend local:
        // había que editar NetworkModule.kt, compilar, y acordarse de
        // revertirlo antes de publicar. Ese "acordarse" es exactamente lo que
        // termina mandando una versión de prueba a producción.
        //
        // 10.0.2.2 es la dirección con la que el emulador de Android alcanza
        // a la máquina que lo hospeda; en un teléfono real no significa nada.
        debug {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:3000/\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"https://api.isita.online/\"")

            // Se firma con la clave de depuración a propósito.
            //
            // Android no instala un APK sin firmar, y esta aplicación se
            // distribuye descargándola desde la landing, no por Play Store.
            // Con esto el APK de publicación se instala igual que hasta ahora,
            // sin agregar una clave nueva que haya que custodiar y que, si se
            // pierde, impide volver a actualizar la aplicación jamás.
            //
            // Si algún día se publica en Play Store hay que generar una clave
            // propia. Conviene hacerlo ANTES de tener usuarios: cambiar la
            // firma obliga a desinstalar y reinstalar a todo el mundo.
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Almacenamiento cifrado (token de sesión y contraseñas sin sincronizar)
    implementation(libs.androidx.security.crypto)
}