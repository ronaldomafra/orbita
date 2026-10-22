package dev.orbita.game

import dev.orbita.game.match.MatchScreen
import dev.orbita.game.spike.SpikeScreen
import ktx.app.KtxGame
import ktx.app.KtxScreen

/** [spikeUnits] > 0 abre o spike técnico da F5 em vez da partida. */
class OrbitaGame(
    private val spikeUnits: Int = 0,
    private val debugAi: Boolean = false,
    private val stressUnits: Int = 0,
) : KtxGame<KtxScreen>() {
    override fun create() {
        if (spikeUnits > 0) {
            addScreen(SpikeScreen(spikeUnits))
            setScreen<SpikeScreen>()
        } else {
            addScreen(MatchScreen(debugAi = debugAi, startUnitsOverride = stressUnits))
            setScreen<MatchScreen>()
        }
    }
}
