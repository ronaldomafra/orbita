package dev.orbita.ai

import dev.orbita.sim.Simulation
import dev.orbita.sim.UnitStore

/**
 * Resumo do estado para a IA, recalculado no máximo uma vez por tick.
 * Só usa o que o jogador também poderia ver (sem informação oculta).
 */
class Observation(val sim: Simulation) {

    val factionCount = sim.factions.size
    val planetCount = sim.planets.size

    /** Power de cada facção na faixa de órbita de cada planeta: `near[faction][planet]`. */
    val near = Array(factionCount) { IntArray(planetCount) }

    /** Power de cada facção a caminho de cada planeta: `incoming[faction][planet]`. */
    val incoming = Array(factionCount) { IntArray(planetCount) }

    /** Grupos de unidades paradas longe de planetas, por facção. */
    val idleClusters: List<MutableList<Cluster>> = List(factionCount) { ArrayList() }

    class Cluster(var x: Float, var y: Float, var power: Int, var count: Int) {
        val radius: Float get() = CLUSTER_CELL
    }

    var tick = -1
        private set

    fun refresh() {
        if (tick == sim.tick) return
        tick = sim.tick
        for (f in 0 until factionCount) {
            near[f].fill(0)
            incoming[f].fill(0)
            idleClusters[f].clear()
        }
        val u = sim.units
        for (i in 0 until u.highWater) {
            if (!u.alive[i]) continue
            val f = u.owner[i]
            val p = u.power[i]
            if (u.mode[i] == UnitStore.MODE_MOVE_PLANET) {
                incoming[f][u.targetPlanet[i]] += p
                continue
            }
            val planet = nearestPlanetInBand(u.x[i], u.y[i])
            if (planet >= 0) near[f][planet] += p
            else if (u.mode[i] == UnitStore.MODE_HOLD) addToCluster(f, u.x[i], u.y[i], p)
        }
        for (list in idleClusters) for (c in list) { c.x /= c.power; c.y /= c.power }
    }

    fun bandRadius(planet: Int): Float {
        val p = sim.planets[planet]
        return sim.config.planetRadius(p.level) + sim.config.orbitGap + sim.config.orbitJitter + BAND_MARGIN
    }

    fun enemyNear(faction: Int, planet: Int): Int {
        var sum = 0
        for (f in 0 until factionCount) if (f != faction) sum += near[f][planet]
        return sum
    }

    fun enemyIncoming(faction: Int, planet: Int): Int {
        var sum = 0
        for (f in 0 until factionCount) if (f != faction) sum += incoming[f][planet]
        return sum
    }

    private fun nearestPlanetInBand(x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (k in 0 until planetCount) {
            val p = sim.planets[k]
            val band = bandRadius(k)
            val dx = p.x - x
            val dy = p.y - y
            val d = dx * dx + dy * dy
            if (d <= band * band && d < bestD) { best = k; bestD = d }
        }
        return best
    }

    /** Agrupa por célula; x/y acumulam soma ponderada e viram centróide no fim do refresh. */
    private fun addToCluster(faction: Int, x: Float, y: Float, power: Int) {
        val cx = (x / CLUSTER_CELL).toInt()
        val cy = (y / CLUSTER_CELL).toInt()
        val list = idleClusters[faction]
        for (c in list) {
            if (c.count > 0 && (c.x / c.power / CLUSTER_CELL).toInt() == cx && (c.y / c.power / CLUSTER_CELL).toInt() == cy) {
                c.x += x * power; c.y += y * power; c.power += power; c.count++
                return
            }
        }
        list.add(Cluster(x * power, y * power, power, 1))
    }

    companion object {
        private const val BAND_MARGIN = 20f
        private const val CLUSTER_CELL = 120f
    }
}
