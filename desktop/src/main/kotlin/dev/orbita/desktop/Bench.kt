package dev.orbita.desktop

import dev.orbita.game.spike.SpikeSim

/** Mede o custo por tick do spike sem renderização: args = unidades, ticks. */
fun main(args: Array<String>) {
    val units = args.getOrNull(0)?.toIntOrNull() ?: 2000
    val ticks = args.getOrNull(1)?.toIntOrNull() ?: 3600
    val sim = SpikeSim(units, seed = 42L)

    repeat(600) { sim.step() } // aquecimento do JIT

    val start = System.nanoTime()
    repeat(ticks) { sim.step() }
    val msPerTick = (System.nanoTime() - start) / 1e6 / ticks

    println("bench units=$units ticks=$ticks msPerTick=%.4f checksum=%d".format(msPerTick, sim.checksum()))
}
