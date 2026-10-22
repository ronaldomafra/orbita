package dev.orbita.game

import dev.orbita.game.spike.SpikeScreen
import ktx.app.KtxGame
import ktx.app.KtxScreen

class OrbitaGame(private val spikeUnits: Int = 2000) : KtxGame<KtxScreen>() {
    override fun create() {
        addScreen(SpikeScreen(spikeUnits))
        setScreen<SpikeScreen>()
    }
}
