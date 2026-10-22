// Partidas bot × bot headless (F6.5). Por enquanto só o esqueleto.
plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":ai"))
}

application { mainClass.set("dev.orbita.arena.ArenaKt") }
