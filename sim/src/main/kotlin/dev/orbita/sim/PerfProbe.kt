package dev.orbita.sim

/**
 * Cenário de estresse para o T-11: 4 bases com [unitsPerBase] unidades cada, que periodicamente
 * mandam tudo para o centro (combate em massa). Mede o custo médio e o pior custo por tick.
 */
object PerfProbe {

    class Result(val msPerTick: Double, val worstMs: Double, val peakUnits: Int, val ticks: Int, val phaseMs: List<Double>)

    fun run(unitsPerBase: Int = 500, ticks: Int = 1800, warmupTicks: Int = 300): Result {
        val base = StandardMaps.quad4()
        val map = base.copy(planets = base.planets.map { if (it.owner >= 0) it.copy(startUnits = unitsPerBase) else it })
        val sim = Simulation(map, seed = 7)
        sim.profile = true
        val center = sim.planets.last()

        var total = 0L
        var worst = 0L
        var peak = 0
        val end = warmupTicks + ticks
        while (sim.tick < end) {
            if (sim.tick % 240 == 0) {
                for (f in 0 until 4) {
                    val home = sim.planets[f]
                    sim.submit(SendUnits(f, home.x, home.y, 140f, Target.PlanetRef(center.id)))
                }
            }
            val t0 = System.nanoTime()
            sim.step()
            val dt = System.nanoTime() - t0
            if (sim.tick == warmupTicks) sim.phaseNanos.fill(0)
            if (sim.tick > warmupTicks) {
                total += dt
                if (dt > worst) worst = dt
            }
            val alive = (0 until 4).sumOf { sim.unitCount(it) }
            if (alive > peak) peak = alive
        }
        return Result(total / 1e6 / ticks, worst / 1e6, peak, ticks, sim.phaseNanos.map { it / 1e6 / ticks })
    }
}
