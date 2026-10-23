import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}


val gdxVersion: String by project
val natives: Configuration by configurations.creating

// Assinatura de release: android/keystore.properties + android/release.jks (fora do git).
val keystoreProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.orbita.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.orbita.spike"
        minSdk = 21
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    sourceSets["main"].jniLibs.srcDirs("libs")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":game"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    listOf("armeabi-v7a", "arm64-v8a", "x86_64").forEach { abi ->
        natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-$abi")
    }
}

// Extrai as .so nativas da libGDX para android/libs/<abi> antes do empacotamento.
val copyAndroidNatives by tasks.registering {
    val nativeJars = natives
    val libsDir = file("libs")
    doFirst {
        nativeJars.files.forEach { jar ->
            val abi = jar.nameWithoutExtension.substringAfter("natives-")
            val outDir = libsDir.resolve(abi).apply { mkdirs() }
            project.copy {
                from(zipTree(jar))
                into(outDir)
                include("*.so")
            }
        }
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }
    .configureEach { dependsOn(copyAndroidNatives) }
