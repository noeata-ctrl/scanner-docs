plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fred.scanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fred.scanner"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Chave fixa: assim cada nova versão instala por cima da anterior sem perder os documentos.
    signingConfigs {
        create("fixo") {
            storeFile = file("scanner.keystore")
            storePassword = "scanner123"
            keyAlias = "scanner"
            keyPassword = "scanner123"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixo")
        }
        debug { signingConfig = signingConfigs.getByName("fixo") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
