package dev.orbita.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import dev.orbita.game.match.MatchScreen
import dev.orbita.game.settings.Settings
import dev.orbita.game.settings.SettingsScreen
import dev.orbita.game.spike.SpikeScreen
import ktx.app.KtxGame
import ktx.app.KtxScreen

/** [spikeUnits] > 0 abre o spike técnico da F5 em vez da partida. */
class OrbitaGame(
    private val spikeUnits: Int = 0,
    private val debugAi: Boolean = false,
    private val stressUnits: Int = 0,
    private val initialZoom: Float = 1f,
) : KtxGame<KtxScreen>() {

    private lateinit var settings: Settings

    override fun create() {
        if (spikeUnits > 0) {
            addScreen(SpikeScreen(spikeUnits))
            setScreen<SpikeScreen>()
            return
        }
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        settings = Settings(Gdx.app.getPreferences(Settings.PREFS_NAME))
        val match = MatchScreen(
            settings = settings,
            onOpenSettings = { setScreen<SettingsScreen>() },
            debugAi = debugAi,
            startUnitsOverride = stressUnits,
            initialZoom = initialZoom,
        )
        addScreen(match)
        addScreen(
            SettingsScreen(
                settings = settings,
                onBack = { setScreen<MatchScreen>() },
                onApplyAndRestart = { match.restartWithSettings(); setScreen<MatchScreen>() },
            ),
        )
        setScreen<MatchScreen>()
    }
}
