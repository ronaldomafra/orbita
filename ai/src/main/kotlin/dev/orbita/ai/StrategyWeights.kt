package dev.orbita.ai

/** Pesos de personalidade da [UtilityBot] (spec §5). Todos os valores são nossos e serão afinados na arena. */
data class StrategyWeights(
    val name: String,
    val expand: Float,
    val attack: Float,
    val defend: Float,
    val upgrade: Float,
    /** Força enviada = necessidade × margem. */
    val safetyMargin: Float,
    /** Penalidade por distância (por 1000 u). */
    val distanceCost: Float,
    /** Preferência por atacar a facção mais forte (0 = indiferente). */
    val targetLeaderBias: Float,
    /** Ruído relativo aplicado às pontuações. */
    val noise: Float,
    val thinkPeriodTicks: Int,
    val maxOrders: Int,
    val maxSources: Int,
)

object Personalities {
    val EXPANSIONIST = StrategyWeights(
        name = "Expansionista", expand = 1.6f, attack = 0.8f, defend = 1.0f, upgrade = 0.9f,
        safetyMargin = 1.2f, distanceCost = 1.0f, targetLeaderBias = 0.2f, noise = 0.15f,
        thinkPeriodTicks = 75, maxOrders = 3, maxSources = 3,
    )
    val AGGRESSIVE = StrategyWeights(
        name = "Agressivo", expand = 1.0f, attack = 1.6f, defend = 0.8f, upgrade = 0.6f,
        safetyMargin = 1.0f, distanceCost = 0.7f, targetLeaderBias = 0.6f, noise = 0.2f,
        thinkPeriodTicks = 60, maxOrders = 3, maxSources = 4,
    )
    val BUILDER = StrategyWeights(
        name = "Construtor", expand = 1.0f, attack = 0.8f, defend = 1.4f, upgrade = 1.6f,
        safetyMargin = 1.4f, distanceCost = 1.2f, targetLeaderBias = 0.3f, noise = 0.15f,
        thinkPeriodTicks = 90, maxOrders = 2, maxSources = 3,
    )

    val ALL = listOf(EXPANSIONIST, AGGRESSIVE, BUILDER)
}
