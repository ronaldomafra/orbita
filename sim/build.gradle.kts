// Núcleo da simulação: Kotlin/JVM puro, sem libGDX nem Android.
plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
