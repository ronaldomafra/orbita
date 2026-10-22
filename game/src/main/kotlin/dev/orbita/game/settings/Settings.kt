package dev.orbita.game.settings

import com.badlogic.gdx.Preferences
import dev.orbita.ai.StrategyWeights
import dev.orbita.sim.SimConfig
import kotlin.math.roundToInt

/** Valores atuais dos [Params], persistidos nas Preferences (Android e desktop). */
class Settings(private val prefs: Preferences?) {

    private val values = FloatArray(Params.all.size) { Params.all[it].default }

    init {
        load()
    }

    operator fun get(p: Param): Float = values[p.index]

    operator fun set(p: Param, value: Float) {
        val snapped = if (p.kind == Kind.FLOAT) value else value.roundToInt().toFloat()
        values[p.index] = snapped.coerceIn(p.min, p.max)
    }

    fun int(p: Param): Int = values[p.index].roundToInt()
    fun bool(p: Param): Boolean = values[p.index] >= 0.5f

    fun resetCategory(category: Category) {
        for (p in Params.all) if (p.category == category) values[p.index] = p.default
    }

    fun load() {
        val prefs = prefs ?: return
        for (p in Params.all) if (prefs.contains(p.key)) set(p, prefs.getFloat(p.key))
    }

    fun save() {
        val prefs = prefs ?: return
        for (p in Params.all) prefs.putFloat(p.key, values[p.index])
        prefs.flush()
    }

    // ---- conversões para os módulos do jogo ----

    fun simConfig(): SimConfig = SimConfig(
        spawnPeriodTicks = int(Params.SPAWN_PERIOD),
        unitSpeed = this[Params.UNIT_SPEED],
        speedVariation = this[Params.SPEED_VARIATION],
        engageRange = this[Params.ENGAGE_RANGE],
        orbitEngageRange = this[Params.ORBIT_ENGAGE],
        clashBase = this[Params.CLASH_BASE],
        planetBaseRadius = this[Params.PLANET_RADIUS],
        orbitGap = this[Params.ORBIT_GAP],
        orbitSpeed = this[Params.ORBIT_SPEED],
        avoidMargin = this[Params.AVOID_MARGIN],
        cloudSpread = this[Params.CLOUD_SPREAD],
        driftAccel = this[Params.DRIFT],
    )

    fun personalities(): List<StrategyWeights> = Params.PERSONALITIES.map { pp ->
        StrategyWeights(
            name = pp.name,
            expand = this[pp.expand],
            attack = this[pp.attack],
            defend = this[pp.defend],
            upgrade = this[pp.upgrade],
            safetyMargin = this[pp.safety],
            distanceCost = this[pp.distance],
            targetLeaderBias = this[pp.leader],
            noise = this[pp.noise],
            thinkPeriodTicks = int(pp.think),
            maxOrders = int(pp.orders),
            maxSources = int(pp.sources),
        )
    }

    companion object {
        const val PREFS_NAME = "orbita-settings"
    }
}
