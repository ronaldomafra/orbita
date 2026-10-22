package dev.orbita.ai

import dev.orbita.sim.Command

/** Contrato de um bot (plano v2 §3.4): lê a observação e devolve comandos da mesma API do jogador. */
interface BotBrain {
    val faction: Int
    val weights: StrategyWeights

    /** Últimas decisões, para o overlay de debug e para logs da arena. */
    val lastDecisions: List<Decision>

    fun isDue(tick: Int): Boolean

    fun decide(obs: Observation): List<Command>
}

enum class DecisionKind { EXPAND, ATTACK, DEFEND, REPAIR, UPGRADE }

data class Decision(
    val kind: DecisionKind,
    val targetPlanet: Int,
    val score: Float,
    val need: Int,
    val sent: Int,
    /** Centros das fontes usadas (x0, y0, x1, y1, …). */
    val sourcePoints: FloatArray,
)
