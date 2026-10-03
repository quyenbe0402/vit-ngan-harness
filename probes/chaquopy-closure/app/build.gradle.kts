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

chaquopy {
    // Chaquopy plugin version 17.0.0 is pinned exactly in build.gradle.kts.
    defaultConfig {
        // Hermes @ eaecc99c declares requires-python = ">=3.11,<3.15"
        // and every core dependency carries the marker python_version >= "3.14".
        // Installing on 3.13 would silently drop every dependency.
        version = "3.14"
        buildPython = listOf("C:/Users/ADMIN/AppData/Roaming/uv/python/cpython-3.14.6-windows-x86_64-none/python.exe")
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
            install("psutil @ git+https://github.com/giampaolo/psutil.git@380bd2b59c67b0e1b04bbf3a90b11744f4f96644")
            install("certifi==2026.5.20")
            install("truststore==0.10.4")
            install("python-dotenv==1.2.2")
            install("fire==0.7.1")
            install("httpx[socks]==0.28.1")
            install("rich==14.3.3")
            install("tenacity==9.1.4")
            install("tomli-w==1.2.0")
            install("ruamel.yaml==0.18.16")
            install("requests==2.33.0")
            install("jinja2==3.1.6")
            install("pydantic==2.13.4")
            install("prompt_toolkit==3.0.52")
            install("croniter==6.0.0")
            install("snowballstemmer==3.1.1")
            install("packaging==26.0")
            install("Markdown==3.10.2")
            install("PyJWT[crypto]==2.13.0")
            install("urllib3>=2.7.0,<3")
            install("websockets==15.0.1")
            install("browser-harness==0.1.13")
            install("pathspec==1.1.1")
            install("fastapi>=0.104.0,<1")
            install("uvicorn>=0.31.0,<1")
            install("python-multipart>=0.0.9,<1")
            install("ptyprocess>=0.7.0,<1")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}