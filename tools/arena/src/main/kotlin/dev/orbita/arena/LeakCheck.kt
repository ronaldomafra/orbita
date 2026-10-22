package dev.orbita.arena

import dev.orbita.ai.BotDriver
import dev.orbita.sim.Simulation
import dev.orbita.sim.StandardMaps

/** Mede memória retida numa partida longa (jogador parado + 3 bots). */
fun main() {
    val sim = Simulation(StandardMaps.quad4(), 42)
    val bots = BotDriver.standard(sim, 42)
    val rt = Runtime.getRuntime()
    fun retainedKb(): Long { System.gc(); Thread.sleep(50); return (rt.totalMemory() - rt.freeMemory()) / 1024 }
    val base = retainedKb()
    for (minute in 1..20) {
        repeat(3600) { bots.beforeStep(); sim.step() }
        if (minute % 5 == 0) println("min=$minute commandLog=${sim.commandLog.size} retainedKB=${retainedKb() - base} units=${(0 until 4).sumOf { sim.unitCount(it) }} capacity=${sim.units.capacity}")
    }
}
