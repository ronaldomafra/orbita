package dev.orbita.sim

/**
 * Partida roteirizada (4 facções no [StandardMaps.quad4]) usada para comparar o estado entre
 * execuções e plataformas (spec R9 / T-10). Os comandos vêm de um RNG próprio, separado da simulação.
 */
object DeterminismProbe {

    class Result(val finalHash: Long, val checkpoints: List<Long>, val simulation: Simulation)

    fun run(seed: Long, ticks: Int = 3600, config: SimConfig = SimConfig()): Result {
        val sim = Simulation(StandardMaps.quad4(), seed, config)
        val script = Rng(seed * 31 + 7)
        val checkpoints = ArrayList<Long>()
        while (sim.tick < ticks) {
            if (sim.tick % 90 == 45) {
                for (f in 0 until sim.factions.size) {
                    val owned = sim.planets.filter { it.owner == f && it.level >= 1 }
                    if (owned.isEmpty()) continue
                    val from = owned[script.nextInt(owned.size)]
                    // Expansão: um dos 2 alvos mais próximos que ainda não são planetas ativos da facção.
                    val candidates = sim.planets.filter { !(it.owner == f && it.level >= 1) }
                        .sortedBy { (it.x - from.x) * (it.x - from.x) + (it.y - from.y) * (it.y - from.y) }
                    if (candidates.isEmpty()) continue
                    val to = candidates[script.nextInt(minOf(2, candidates.size))]
                    sim.submit(SendUnits(f, from.x, from.y, 90f, Target.PlanetRef(to.id), maxPower = 150))
                }
            }
            sim.step()
            if (sim.tick % 60 == 0) checkpoints += sim.stateHash()
        }
        return Result(sim.stateHash(), checkpoints, sim)
    }
}
