// Camada de jogo: renderização, input e telas (libGDX + KTX).
plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(17) }

val gdxVersion: String by project
val ktxVersion: String by project

dependencies {
    api(project(":ai"))
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    api("io.github.libktx:ktx-app:$ktxVersion")
    api("io.github.libktx:ktx-graphics:$ktxVersion")
}
