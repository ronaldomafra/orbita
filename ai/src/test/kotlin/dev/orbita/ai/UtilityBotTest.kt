package dev.orbita.ai

import dev.orbita.sim.Simulation
import dev.orbita.sim.StandardMaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UtilityBotTest {

    private fun allBots(sim: Simulation, seed: Long) = BotDriver(
        sim,
        (0 until 4).map { f -> UtilityBot(f, Personalities.ALL[f % Personalities.ALL.size], seed) },
    )

    private fun play(seed: Long, ticks: Int, driver: (Simulation) -> BotDriver): Simulation {
        val sim = Simulation(StandardMaps.quad4(), seed)
        val d = driver(sim)
        while (sim.tick < ticks && sim.winner < 0) {
            d.beforeStep()
            sim.step()
        }
        return sim
    }

    @Test
    fun `bots expandem para neutros no primeiro minuto`() {
        val sim = play(seed = 3, ticks = 60 * 60) { BotDriver.standard(it, seed = 3) }
        for (f in 1..3) {
            val owned = sim.planets.count { it.owner == f && it.level >= 1 }
            assertTrue(owned >= 2, "facção $f deveria ter capturado ao menos 1 neutro (tem $owned planetas)")
        }
    }

    @Test
    fun `bots vencem um jogador parado`() {
        val sim = play(seed = 5, ticks = 60 * 60 * 15) { BotDriver.standard(it, seed = 5) }
        assertTrue(sim.factions[0].eliminated, "o jogador parado deveria ser eliminado em 15 min")
    }

    @Test
    fun `partida bot contra bot e deterministica`() {
        val a = play(seed = 11, ticks = 60 * 120) { allBots(it, 11) }
        val b = play(seed = 11, ticks = 60 * 120) { allBots(it, 11) }
        assertEquals(a.stateHash(), b.stateHash())
    }
}
