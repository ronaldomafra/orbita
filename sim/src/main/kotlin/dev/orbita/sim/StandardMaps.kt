package dev.orbita.sim

/** Mapas próprios do Órbita (spec §3). */
object StandardMaps {

    /** Arena 1600×760, simétrica em 4 quadrantes: 4 bases e 12 neutros. */
    fun quad4(): MapDef {
        val cx = 800f
        val cy = 380f
        val planets = ArrayList<PlanetDef>()
        val quadrants = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)
        quadrants.forEachIndexed { faction, (sx, sy) ->
            planets += PlanetDef(cx + sx * 620f, cy + sy * 250f, maxLevel = 3, owner = faction, level = 1, startUnits = 100)
        }
        // Expansão fácil perto de cada base.
        quadrants.forEach { (sx, sy) -> planets += PlanetDef(cx + sx * 420f, cy + sy * 160f, maxLevel = 1) }
        // Disputa entre bases vizinhas.
        listOf(-1, 1).forEach { sy -> planets += PlanetDef(cx, cy + sy * 270f, maxLevel = 2) }
        listOf(-1, 1).forEach { sx -> planets += PlanetDef(cx + sx * 620f, cy, maxLevel = 2) }
        // Objetivos de meio de jogo no centro.
        quadrants.forEach { (sx, sy) ->
            planets += PlanetDef(cx + sx * 140f, cy + sy * 90f, maxLevel = if (sx == sy) 3 else 2)
        }
        return MapDef(width = 1600f, height = 760f, factionCount = 4, planets = planets)
    }
}
