import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing material is supplied through signing.properties, which is gitignored.
// The keystore itself is never committed to this repository.
val signingPropsFile = rootProject.file("signing.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) signingPropsFile.inputStream().use { load(it) }
}

android {
    namespace = "dev.vitngan.termuxbridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.vitngan.termuxbridge"
        minSdk = 24
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0-proto"
    }

    signingConfigs {
        create("termuxCompat") {
            if (signingPropsFile.exists()) {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingPropsFile.exists()) signingConfig = signingConfigs.getByName("termuxCompat")
        }
    }

    lint {
        // Prototype, not a store submission. Termux targets API 28 deliberately
        // and this mirrors it so the shared-UID/signing match holds.
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}