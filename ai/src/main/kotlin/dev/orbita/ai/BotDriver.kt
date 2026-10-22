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
        /** Bots padrão do MVP: facções 1..3 com as três personalidades. */
        fun standard(sim: Simulation, seed: Long, dropRate: Int = 0): BotDriver {
            val bots = (1 until sim.factions.size).map { f ->
                sim.factions[f].dropRate = dropRate
                UtilityBot(f, Personalities.ALL[(f - 1) % Personalities.ALL.size], seed)
            }
            return BotDriver(sim, bots)
        }
    }
}
