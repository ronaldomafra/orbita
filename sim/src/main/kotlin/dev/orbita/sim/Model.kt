package dev.orbita.sim

const val NO_FACTION = -1

/** Planeta (spec R2). Só a simulação altera o estado. */
class Planet internal constructor(
    val id: Int,
    val x: Float,
    val y: Float,
    val maxLevel: Int,
) {
    var owner: Int = NO_FACTION
        internal set
    var level: Int = 0
        internal set
    var health: Int = 0
        internal set
    var growth: Int = 0
        internal set
}

class Faction internal constructor(val id: Int) {
    /** Handicap de dificuldade: descarta 1 de cada [dropRate] unidades produzidas (0 = nunca). ref: LAC-005 */
    var dropRate: Int = 0
    var eliminated: Boolean = false
        internal set

    private var dropCounter = 0

    internal fun dropUnit(): Boolean {
        if (dropRate <= 0) return false
        dropCounter++
        return dropCounter % dropRate == 0
    }
}

data class PlanetDef(
    val x: Float,
    val y: Float,
    val maxLevel: Int,
    val owner: Int = NO_FACTION,
    val level: Int = 0,
    val startUnits: Int = 0,
    /** null = saúde máxima do nível. */
    val health: Int? = null,
    val growth: Int = 0,
)

data class MapDef(
    val width: Float,
    val height: Float,
    val factionCount: Int,
    val planets: List<PlanetDef>,
)
