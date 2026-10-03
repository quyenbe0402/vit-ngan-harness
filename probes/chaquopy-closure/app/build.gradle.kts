plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "dev.vitngan.probe"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.vitngan.probe.chaquopy"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-probe"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val localWheelDir = file("$rootDir/../native-wheels")

chaquopy {
    // Chaquopy plugin version 17.0.0 is pinned exactly in build.gradle.kts.
    defaultConfig {
        // Hermes @ eaecc99c declares requires-python = ">=3.11,<3.15"
        // and every core dependency carries the marker python_version >= "3.14".
        // Installing on 3.13 would silently drop every dependency.
        version = "3.14"
        buildPython = listOf("C:/Users/ADMIN/AppData/Roaming/uv/python/cpython-3.14.6-windows-x86_64-none/python.exe")
        pip {
            // Self-rebuilt Android arm64 cp314 wheel (M0-008I).
            // Wheel tag android_24_arm64_v8a matches Chaquopy's own naming.
            install("typing-extensions>=4.12")
            install("pydantic_core @ file:///" + localWheelDir.toPath().resolve("pydantic_core-2.46.4-cp314-cp314-android_24_arm64_v8a.whl").toString().replace("\\", "/").removePrefix("C:/").let { "C:/" + it })
        }
    }
    sourceSets {
        getByName("main") { srcDir("src/main/python") }
    }
    // The real Android-active closure from eaecc99c, verbatim.
    defaultConfig {
        pip {
            // NOTE: psutil for sys_platform == "android" is declared upstream as a
            // git VCS dependency. It is deliberately included here to capture the
            // real failure rather than to hide it.
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}