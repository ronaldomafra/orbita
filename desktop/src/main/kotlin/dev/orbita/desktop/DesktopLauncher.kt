package dev.orbita.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import dev.orbita.game.OrbitaGame

fun main(args: Array<String>) {
    val units = args.firstOrNull()?.toIntOrNull() ?: 0
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Órbita")
        setWindowedMode(1520, 720)
        useVsync(true)
        setForegroundFPS(60)
    }
    Lwjgl3Application(OrbitaGame(units), config)
}
