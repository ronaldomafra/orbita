package dev.orbita.sim

import dev.orbita.sim.UnitStore.Companion.MODE_HOLD
import dev.orbita.sim.UnitStore.Companion.MODE_ORBIT
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Testes do núcleo (spec 08-plano-mvp.md §6). */
class SimulationTest {

    private val noProduction = SimConfig(productionEnabled = false)

    private fun map(vararg planets: PlanetDef, factions: Int = 2) =
        MapDef(width = 1000f, height = 600f, factionCount = factions, planets = planets.toList())

    private fun Simulation.run(ticks: Int) = repeat(ticks) { step() }

    private fun Simulation.aliveUnits(faction: Int): List<Int> =
        (0 until units.highWater).filter { units.alive[it] && units.owner[it] == faction }

    @Test
    fun `T-01 planeta L2 isolado produz 40 unidades em 1280 ticks`() {
        val sim = Simulation(map(PlanetDef(500f, 300f, maxLevel = 2, owner = 0, level = 2), factions = 1), seed = 1)
        sim.run(1280)
        assertEquals(40, sim.unitCount(0))
        assertEquals(40, sim.totalPower(0))
    }

    @Test
    fun `T-02 tabelas de custo e saude`() {
        assertEquals(listOf(100, 100, 100, 200, 300, 400), (0..5).map(Rules::growthCost))
        assertEquals(listOf(100, 100, 100, 100, 200, 300), (0..5).map(Rules::maxHealth))
        assertEquals(listOf(0, 2, 4, 6), (0..3).map(Rules::unitsPerSpawn))
    }

    @Test
    fun `T-03 choque 5 contra 3 deixa uma unidade de power 2`() {
        val sim = Simulation(map(), seed = 1, config = noProduction)
        sim.addUnit(owner = 0, power = 5, x = 100f, y = 100f)
        sim.addUnit(owner = 1, power = 3, x = 130f, y = 100f)
        sim.run(60)
        val survivors = sim.aliveUnits(0)
        assertEquals(1, survivors.size)
        assertEquals(2, sim.units.power[survivors.single()])
        assertEquals(0, sim.unitCount(1))
    }

    @Test
    fun `T-04 ataque destroi planeta inimigo e mantem o dono`() {
        val sim = Simulation(map(PlanetDef(500f, 300f, maxLevel = 2, owner = 1, level = 1)), seed = 1, config = noProduction)
        sim.addUnit(owner = 0, power = 130, x = 400f, y = 300f)
        sim.submit(SendUnits(0, 400f, 300f, 5f, Target.PlanetRef(0)))
        sim.run(120)
        val p = sim.planets[0]
        assertEquals(0, p.level)
        assertEquals(1, p.owner)
        assertEquals(0, p.health)
        val attacker = sim.aliveUnits(0).single()
        assertEquals(30, sim.units.power[attacker])
        // AJ-05: após destruir, a unidade vai até o centro do planeta e fica ali.
        assertEquals(MODE_HOLD, sim.units.mode[attacker])
        val d = hypot(sim.units.x[attacker] - p.x, sim.units.y[attacker] - p.y)
        assertTrue(d < 15f, "deveria estar no centro do planeta destruído (distância $d)")
    }

    @Test
    fun `T-12 unidades contornam planeta ativo e atravessam planeta sem dono`() {
        fun minDistance(level: Int, owner: Int): Float {
            val sim = Simulation(map(PlanetDef(500f, 300f, maxLevel = 2, owner = owner, level = level)), seed = 1, config = noProduction)
            sim.addUnit(owner = 0, power = 1, x = 300f, y = 300f)
            sim.submit(SendUnits(0, 300f, 300f, 5f, Target.Point(700f, 300f)))
            var min = Float.MAX_VALUE
            repeat(300) {
                sim.step()
                val u = sim.aliveUnits(0).single()
                min = minOf(min, hypot(sim.units.x[u] - 500f, sim.units.y[u] - 300f))
            }
            val u = sim.aliveUnits(0).single()
            assertTrue(hypot(sim.units.x[u] - 700f, sim.units.y[u] - 300f) < 10f, "deveria chegar ao destino")
            return min
        }
        val radius = SimConfig().planetRadius(1)
        assertTrue(minDistance(level = 1, owner = 1) >= radius, "não pode atravessar planeta ativo")
        assertTrue(minDistance(level = 0, owner = NO_FACTION) < radius, "planeta sem dono não bloqueia")
    }

    @Test
    fun `T-13 enxame parado forma nuvem viva em torno do ponto`() {
        val sim = Simulation(map(), seed = 1, config = noProduction)
        repeat(100) { sim.addUnit(owner = 0, power = 1, x = 200f, y = 300f) }
        sim.submit(SendUnits(0, 200f, 300f, 5f, Target.Point(600f, 300f)))
        sim.run(400)
        val ids = sim.aliveUnits(0)
        val xs = ids.map { sim.units.x[it] }
        val ys = ids.map { sim.units.y[it] }
        val cx = xs.average()
        val cy = ys.average()
        assertTrue(hypot((cx - 600).toFloat(), (cy - 300).toFloat()) < 8f, "nuvem centrada no ponto")
        val spread = ids.maxOf { hypot(sim.units.x[it] - 600f, sim.units.y[it] - 300f) }
        assertTrue(spread in 20f..90f, "nuvem com raio ~5·√100 (obtido $spread)")
        val before = ids.map { sim.units.x[it] to sim.units.y[it] }
        sim.run(60)
        val moved = ids.indices.count { k -> before[k] != (sim.units.x[ids[k]] to sim.units.y[ids[k]]) }
        assertEquals(ids.size, moved, "todas as unidades paradas continuam se mexendo")
    }

    @Test
    fun `T-05 cabo de guerra vira o dono do neutro`() {
        val sim = Simulation(map(PlanetDef(500f, 300f, maxLevel = 1, owner = 0, level = 0, growth = 40)), seed = 1, config = noProduction)
        sim.addUnit(owner = 1, power = 50, x = 400f, y = 300f)
        sim.submit(SendUnits(1, 400f, 300f, 5f, Target.PlanetRef(0)))
        sim.run(120)
        val p = sim.planets[0]
        assertEquals(1, p.owner)
        assertEquals(10, p.growth)
        assertEquals(0, p.level)
        assertEquals(0, sim.unitCount(1))
    }

    @Test
    fun `T-06 upgrade de L1 para L2 e L3`() {
        val sim = Simulation(map(PlanetDef(500f, 300f, maxLevel = 3, owner = 0, level = 1)), seed = 1, config = noProduction)
        sim.addUnit(owner = 0, power = 100, x = 400f, y = 300f)
        sim.submit(SendUnits(0, 400f, 300f, 5f, Target.PlanetRef(0)))
        sim.run(120)
        val p = sim.planets[0]
        assertEquals(2, p.level)
        assertEquals(100, p.health)
        assertEquals(0, p.growth)

        sim.addUnit(owner = 0, power = 100, x = 400f, y = 300f)
        sim.submit(SendUnits(0, 400f, 300f, 5f, Target.PlanetRef(0)))
        sim.run(120)
        assertEquals(3, p.level)
        assertEquals(0, sim.unitCount(0))
    }

    @Test
    fun `T-07 planeta no nivel maximo e saude cheia recusa a unidade`() {
        val sim = Simulation(map(PlanetDef(500f, 300f, maxLevel = 1, owner = 0, level = 1)), seed = 1, config = noProduction)
        sim.addUnit(owner = 0, power = 7, x = 400f, y = 300f)
        sim.submit(SendUnits(0, 400f, 300f, 5f, Target.PlanetRef(0)))
        sim.run(120)
        val u = sim.aliveUnits(0).single()
        assertEquals(7, sim.units.power[u])
        assertEquals(MODE_ORBIT, sim.units.mode[u])
        assertEquals(0, sim.units.home[u])
    }

    @Test
    fun `T-08 dono reconstroi planeta destruido`() {
        val sim = Simulation(map(PlanetDef(500f, 300f, maxLevel = 2, owner = 0, level = 0)), seed = 1, config = noProduction)
        sim.addUnit(owner = 0, power = 100, x = 400f, y = 300f)
        sim.submit(SendUnits(0, 400f, 300f, 5f, Target.PlanetRef(0)))
        sim.run(120)
        val p = sim.planets[0]
        assertEquals(1, p.level)
        assertEquals(0, p.owner)
        assertEquals(100, p.health)
    }

    @Test
    fun `T-09 eliminacao e vitoria`() {
        val sim = Simulation(
            map(
                PlanetDef(200f, 300f, maxLevel = 1, owner = 0, level = 1),
                PlanetDef(800f, 300f, maxLevel = 1, owner = 1, level = 0),
                PlanetDef(500f, 500f, maxLevel = 1, owner = 2, level = 0),
                factions = 3,
            ),
            seed = 1, config = noProduction,
        )
        sim.addUnit(owner = 2, power = 1, x = 500f, y = 100f) // só unidades: ainda viva
        sim.step()
        assertTrue(sim.factions[1].eliminated)
        assertFalse(sim.factions[2].eliminated)
        assertEquals(NO_FACTION, sim.winner)

        sim.addUnit(owner = 0, power = 5, x = 500f, y = 110f)
        sim.run(60)
        assertTrue(sim.factions[2].eliminated)
        assertEquals(0, sim.winner)
    }

    @Test
    fun `T-10 determinismo com comandos e replay`() {
        val a = DeterminismProbe.run(seed = 12345)
        val b = DeterminismProbe.run(seed = 12345)
        assertEquals(a.checkpoints, b.checkpoints, "mesma seed + mesmos comandos devem gerar o mesmo estado")

        val c = DeterminismProbe.run(seed = 999)
        assertNotEquals(a.finalHash, c.finalHash, "seeds diferentes devem divergir")

        val replayed = Simulation.replay(StandardMaps.quad4(), 12345, SimConfig(), a.simulation.commandLog, untilTick = 3600)
        assertEquals(a.finalHash, replayed.stateHash(), "replay a partir do log")

        // Sanidade: a partida roteirizada precisa ter disputa real, não só unidades paradas.
        val sim = a.simulation
        assertTrue(sim.planets.count { it.level >= 1 } > 4, "neutros devem ter sido capturados")
        val produced = 4 * 100 + (0 until 3600 step 128).count() * 2 * 4 // mínimo: bases L1
        assertTrue((0 until 4).sumOf { sim.totalPower(it) } < produced, "deve ter havido combate")
        println("T-10 hash=${a.finalHash} planetas>=L1=${sim.planets.count { it.level >= 1 }} " +
            "power=${(0 until 4).map { sim.totalPower(it) }} donos=${sim.planets.map { it.owner }}")
    }
}
