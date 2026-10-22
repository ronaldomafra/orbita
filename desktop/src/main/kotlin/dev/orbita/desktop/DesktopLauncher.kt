package dev.orbita.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import dev.orbita.game.OrbitaGame

fun main(args: Array<String>) {
    val units = args.firstOrNull()?.toIntOrNull() ?: 2000
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Órbita — spike")
        setWindowedMode(1520, 720)
        useVsync(true)
        setForegroundFPS(60)
    }
    Lwjgl3Application(OrbitaGame(units), config)
}
