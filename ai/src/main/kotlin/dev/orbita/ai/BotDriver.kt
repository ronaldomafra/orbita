package dev.orbita.ai

import dev.orbita.sim.Simulation

/** Chama os bots no tick certo e entrega os comandos à simulação. Usar antes de cada `sim.step()`. */
class BotDriver(val sim: Simulation, val bots: List<BotBrain>) {

    private val observation = Observation(sim)

    fun beforeStep() {
        for (bot in bots) {
            if (!bot.isDue(sim.tick)) continue
            for (command in bot.decide(observation)) sim.submit(command)
        }
    }

    companion object {
        /**
         * Bots padrão do MVP: facções 1..3 com as personalidades de [personalities] (em rodízio),
         * handicap [dropRate] e início em [startTick].
         */
        fun standard(
            sim: Simulation,
            seed: Long,
            dropRate: Int = 0,
            personalities: List<StrategyWeights> = Personalities.ALL,
            startTick: Int = UtilityBot.AI_START_TICK,
        ): BotDriver {
            val bots = (1 until sim.factions.size).map { f ->
                sim.factions[f].dropRate = dropRate
                UtilityBot(f, personalities[(f - 1) % personalities.size], seed, startTick = startTick)
            }
            return BotDriver(sim, bots)
        }
    }
}
