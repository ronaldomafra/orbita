package dev.orbita.ai

import dev.orbita.sim.MapLibrary
import dev.orbita.sim.NO_FACTION
import dev.orbita.sim.Simulation
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Valida todos os mapas: geometria jogável, bases justas e uma partida curta só com bots. */
class MapLibraryTest {

    @Test
    fun `mapas tem geometria valida`() {
        assertEquals(MapLibrary.all.size, MapLibrary.all.map { it.id }.toSet().size, "ids únicos")
        for (info in MapLibrary.all) {
            val map = info.build()
            val ps = map.planets
            for (p in ps) {
                assertTrue(p.x in 60f..map.width - 60f && p.y in 60f..map.height - 60f, "${info.id}: planeta fora da área (${p.x}, ${p.y})")
            }
            for (i in ps.indices) for (j in i + 1 until ps.size) {
                val d = hypot(ps[i].x - ps[j].x, ps[i].y - ps[j].y)
                assertTrue(d >= MIN_SPACING, "${info.id}: planetas $i e $j muito próximos ($d)")
            }
            val bases = ps.filter { it.owner != NO_FACTION }
            assertEquals(map.factionCount, bases.size, "${info.id}: uma base por facção")
            assertEquals((0 until map.factionCount).toSet(), bases.map { it.owner }.toSet(), "${info.id}: facções 0..n-1")
            // Justiça: distância de cada base ao centro do mapa é a mesma (± 1%).
            val dists = bases.map { hypot(it.x - map.width / 2, it.y - map.height / 2) }
            assertTrue(dists.max() - dists.min() <= dists.max() * 0.01f, "${info.id}: bases a distâncias diferentes do centro $dists")
        }
    }

    @Test
    fun `cada mapa roda uma partida curta so com bots`() {
        for (info in MapLibrary.all) {
            val map = info.build()
            val sim = Simulation(map, seed = 21)
            val driver = BotDriver(sim, (0 until map.factionCount).map { f -> UtilityBot(f, Personalities.ALL[f % 3], 21) })
            while (sim.tick < 60 * 90 && sim.winner == NO_FACTION) {
                driver.beforeStep()
                sim.step()
            }
            val active = sim.planets.count { it.level >= 1 }
            assertTrue(active > map.factionCount, "${info.id}: nenhum neutro foi capturado em 90 s")
        }
    }

    private companion object {
        const val MIN_SPACING = 110f
    }
}
