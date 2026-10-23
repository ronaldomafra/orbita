package dev.orbita.game.settings

import dev.orbita.ai.Personalities
import dev.orbita.ai.StrategyWeights
import dev.orbita.ai.UtilityBot
import dev.orbita.sim.SimConfig

enum class Category(val title: String, val note: String) {
    MATCH("Partida", "Vale na próxima partida (Aplicar e reiniciar)."),
    SIMULATION("Simulação", "Vale na próxima partida (Aplicar e reiniciar)."),
    AI("IA", "Vale na próxima partida (Aplicar e reiniciar)."),
    VISUAL("Visual", "Vale ao voltar para a partida."),
    CAMERA("Câmera", "Vale ao voltar para a partida."),
}

enum class Kind { FLOAT, INT, BOOL }

/** Um parâmetro ajustável no menu. A tela de ajustes é gerada a partir de [Params.all]. */
class Param internal constructor(
    val index: Int,
    val key: String,
    val label: String,
    val category: Category,
    val group: String?,
    val min: Float,
    val max: Float,
    val step: Float,
    val default: Float,
    val kind: Kind,
)

/** Registro de todos os parâmetros, com padrões vindos da simulação, da IA e do visual atuais. */
object Params {
    private val list = ArrayList<Param>()
    val all: List<Param> get() = list

    private val sim = SimConfig()

    private fun p(key: String, label: String, category: Category, min: Float, max: Float, step: Float, default: Float,
                  kind: Kind = Kind.FLOAT, group: String? = null): Param =
        Param(list.size, key, label, category, group, min, max, step, default, kind).also { list += it }

    private fun bool(key: String, label: String, category: Category, default: Boolean): Param =
        p(key, label, category, 0f, 1f, 1f, if (default) 1f else 0f, Kind.BOOL)

    // ---- Partida ----
    val START_UNITS = p("match.startUnits", "Unidades iniciais por base", Category.MATCH, 10f, 1000f, 10f, 100f, Kind.INT)
    val BOT_HANDICAP = p("match.botHandicap", "Handicap dos bots (descarta 1 a cada N; 0 = sem)", Category.MATCH, 0f, 8f, 1f, 0f, Kind.INT)

    // ---- Simulação ----
    val SPAWN_PERIOD = p("sim.spawnPeriod", "Intervalo de produção (ticks)", Category.SIMULATION, 16f, 512f, 8f, sim.spawnPeriodTicks.toFloat(), Kind.INT)
    val UNIT_SPEED = p("sim.unitSpeed", "Velocidade das unidades", Category.SIMULATION, 40f, 500f, 5f, sim.unitSpeed)
    val SPEED_VARIATION = p("sim.speedVariation", "Variação de velocidade", Category.SIMULATION, 0f, 0.5f, 0.01f, sim.speedVariation)
    val ENGAGE_RANGE = p("sim.engageRange", "Alcance de atração (em movimento)", Category.SIMULATION, 10f, 200f, 5f, sim.engageRange)
    val ORBIT_ENGAGE = p("sim.orbitEngageRange", "Alcance de atração (em órbita)", Category.SIMULATION, 5f, 200f, 5f, sim.orbitEngageRange)
    val CLASH_BASE = p("sim.clashBase", "Distância de choque", Category.SIMULATION, 2f, 20f, 0.5f, sim.clashBase)
    val PLANET_RADIUS = p("sim.planetRadius", "Raio base do planeta", Category.SIMULATION, 20f, 90f, 1f, sim.planetBaseRadius)
    val ORBIT_GAP = p("sim.orbitGap", "Distância da órbita", Category.SIMULATION, 5f, 90f, 1f, sim.orbitGap)
    val ORBIT_SPEED = p("sim.orbitSpeed", "Velocidade orbital", Category.SIMULATION, 5f, 150f, 1f, sim.orbitSpeed)
    val AVOID_MARGIN = p("sim.avoidMargin", "Folga ao contornar planetas", Category.SIMULATION, 0f, 60f, 1f, sim.avoidMargin)
    val CLOUD_SPREAD = p("sim.cloudSpread", "Espalhamento da nuvem parada", Category.SIMULATION, 0f, 20f, 0.5f, sim.cloudSpread)
    val DRIFT = p("sim.driftAccel", "Movimento \"vivo\" das paradas", Category.SIMULATION, 0f, 0.6f, 0.01f, sim.driftAccel)

    // ---- IA ----
    val AI_START = p("ai.startTick", "Bots começam a agir (ticks)", Category.AI, 0f, 1800f, 30f, UtilityBot.AI_START_TICK.toFloat(), Kind.INT)

    /** Pesos de cada personalidade, agrupados pelo nome. */
    class PersonalityParams(base: StrategyWeights) {
        private val prefix = "ai." + base.name.lowercase()
        private val g = base.name
        val expand = p("$prefix.expand", "Expandir", Category.AI, 0f, 3f, 0.05f, base.expand, group = g)
        val attack = p("$prefix.attack", "Atacar", Category.AI, 0f, 3f, 0.05f, base.attack, group = g)
        val defend = p("$prefix.defend", "Defender", Category.AI, 0f, 3f, 0.05f, base.defend, group = g)
        val upgrade = p("$prefix.upgrade", "Evoluir planetas", Category.AI, 0f, 3f, 0.05f, base.upgrade, group = g)
        val safety = p("$prefix.safety", "Margem de segurança", Category.AI, 0.5f, 3f, 0.05f, base.safetyMargin, group = g)
        val distance = p("$prefix.distance", "Custo da distância", Category.AI, 0f, 4f, 0.05f, base.distanceCost, group = g)
        val leader = p("$prefix.leader", "Foco no líder", Category.AI, 0f, 2f, 0.05f, base.targetLeaderBias, group = g)
        val noise = p("$prefix.noise", "Imprevisibilidade", Category.AI, 0f, 1f, 0.01f, base.noise, group = g)
        val think = p("$prefix.think", "Intervalo de decisão (ticks)", Category.AI, 15f, 400f, 5f, base.thinkPeriodTicks.toFloat(), Kind.INT, g)
        val orders = p("$prefix.orders", "Ordens por decisão", Category.AI, 1f, 8f, 1f, base.maxOrders.toFloat(), Kind.INT, g)
        val sources = p("$prefix.sources", "Fontes por ordem", Category.AI, 1f, 8f, 1f, base.maxSources.toFloat(), Kind.INT, g)
        val name = base.name
    }

    val PERSONALITIES = Personalities.ALL.map { PersonalityParams(it) }

    // ---- Visual ----
    val UNIT_SIZE = p("visual.unitSize", "Tamanho das unidades", Category.VISUAL, 5f, 35f, 0.5f, 15f)
    val UNIT_CORE = p("visual.unitCore", "Brilho do núcleo", Category.VISUAL, 1f, 14f, 0.5f, 5.5f)
    val COMET_LENGTH = p("visual.cometLength", "Rastro em trânsito", Category.VISUAL, 0f, 20f, 0.5f, 7f)
    val ORBIT_TAIL = p("visual.orbitTail", "Rastro em órbita", Category.VISUAL, 0f, 12f, 0.5f, 4f)
    val ORBIT_TILT = p("visual.orbitTilt", "Inclinação da órbita (1 = círculo)", Category.VISUAL, 0.15f, 1f, 0.01f, 0.38f)
    val AURA = p("visual.aura", "Intensidade da aura", Category.VISUAL, 0f, 3f, 0.05f, 1f)
    val PULSE = p("visual.pulse", "Pulso na produção", Category.VISUAL, 0f, 0.4f, 0.01f, 0.1f)
    val NEBULA = p("visual.nebula", "Opacidade da nebulosa", Category.VISUAL, 0f, 1f, 0.01f, 0.28f)
    val STARS = p("visual.stars", "Quantidade de estrelas", Category.VISUAL, 0f, 4000f, 100f, 1400f, Kind.INT)
    val SHOW_FPS = bool("visual.showFps", "Mostrar fps", Category.VISUAL, true)
    val SHOW_PROFILES = bool("visual.showProfiles", "Mostrar perfil dos bots", Category.VISUAL, true)
    val DEBUG_AI = bool("visual.debugAi", "Mostrar decisões da IA", Category.VISUAL, false)

    // ---- Câmera ----
    val MAX_PITCH = p("camera.maxPitch", "Inclinação máxima ao aproximar (°)", Category.CAMERA, 0f, 70f, 1f, 42f)
    val FOV = p("camera.fov", "Campo de visão (°)", Category.CAMERA, 20f, 80f, 1f, 40f)
    val MIN_ZOOM = p("camera.minZoom", "Zoom máximo de aproximação", Category.CAMERA, 0.2f, 0.95f, 0.01f, 0.42f)
    val MAX_ZOOM = p("camera.maxZoom", "Zoom máximo de afastamento", Category.CAMERA, 1f, 4f, 0.05f, 2.2f)
    val EDGE_RESISTANCE = p("camera.edgeResistance", "Resistência além da borda", Category.CAMERA, 0.05f, 1f, 0.01f, 0.45f)
    val RETURN_RATE = p("camera.returnRate", "Velocidade de retorno da borda", Category.CAMERA, 0.5f, 20f, 0.5f, 6f)
}
