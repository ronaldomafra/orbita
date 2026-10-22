plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }

val gdxVersion: String by project

dependencies {
    implementation(project(":game"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}

application { mainClass.set("dev.orbita.desktop.DesktopLauncherKt") }

// Benchmark headless do spike: ./gradlew :desktop:bench -Punits=2000
tasks.register<JavaExec>("bench") {
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.orbita.desktop.BenchKt")
    args = listOf((project.findProperty("units") ?: "2000").toString(), "3600")
}
