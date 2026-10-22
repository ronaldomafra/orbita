package dev.orbita.desktop

import dev.orbita.sim.DeterminismProbe
import dev.orbita.sim.PerfProbe

/** Mesmo cálculo do modo `probe` do Android, para comparar hashes entre plataformas (T-10). */
fun main(args: Array<String>) {
    val seed = args.firstOrNull()?.toLongOrNull() ?: 12345L
    val result = DeterminismProbe.run(seed)
    println("seed=$seed finalHash=${result.finalHash} checkpoints=${result.checkpoints.hashCode()}")

    val perf = PerfProbe.run()
    println("perf peakUnits=${perf.peakUnits} msPerTick=%.3f worstMs=%.3f phases=%s".format(perf.msPerTick, perf.worstMs, perf.phaseMs.joinToString("/") { "%.2f".format(it) }))
}
