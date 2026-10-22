package dev.orbita.sim

import kotlin.math.max
import kotlin.math.sqrt

/** Regras de progressão do planeta (spec R2). */
object Rules {
    // ref: MECH-001
    fun growthCost(level: Int): Int = if (level < 3) 100 else (level - 1) * 100

    // ref: MECH-001
    fun maxHealth(level: Int): Int = if (level < 4) 100 else (level - 2) * 100

    // ref: MECH-003 — unidades por leva
    fun unitsPerSpawn(level: Int): Int = 2 * level
}

/**
 * Parâmetros ajustáveis da simulação. Distâncias em unidades de mapa (u), velocidades em u/s.
 * Valores marcados `design` são decisões nossas (05-parametros.md); os demais vêm da referência.
 */
data class SimConfig(
    val tickHz: Int = 60,                      // ref: MECH-009
    val spawnPeriodTicks: Int = 128,           // ref: MECH-003
    val productionEnabled: Boolean = true,
    val planetBaseRadius: Float = 40f,         // design
    val unitSpeed: Float = 150f,               // design
    val orbitGap: Float = 14f,                 // design
    val orbitJitter: Float = 10f,              // design
    val orbitSpeed: Float = 45f,               // design (velocidade tangencial)
    val arriveMargin: Float = 4f,              // design
    val engageRange: Float = 120f,             // design
    val clashBase: Float = 6f,                 // design: alcance de choque = clashBase·√power
    val scanPeriodTicks: Int = 4,              // D14; potência de 2
) {
    init {
        require(scanPeriodTicks > 0 && scanPeriodTicks and (scanPeriodTicks - 1) == 0) { "scanPeriodTicks deve ser potência de 2" }
    }

    val speedPerTick: Float get() = unitSpeed / tickHz

    /** design: +30% por nível (a referência dobra o raio por nível, o que fica grande demais no nosso mapa). */
    fun planetRadius(level: Int): Float = planetBaseRadius * (1f + 0.3f * (max(level, 1) - 1))

    fun clashDistance(power: Int): Float = clashBase * sqrt(power.toFloat())
}
