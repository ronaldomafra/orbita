// IA dos bots: Kotlin/JVM puro; depende só de :sim.
plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":sim"))
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
