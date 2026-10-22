package dev.orbita.game.match

import com.badlogic.gdx.graphics.Color
import dev.orbita.sim.NO_FACTION

/** Paleta própria do Órbita. A facção 0 é sempre o jogador. */
object Palette {
    val background = Color(0.015f, 0.018f, 0.04f, 1f)
    val neutral = Color(0.55f, 0.58f, 0.64f, 1f)

    private val factions = arrayOf(
        Color(0.25f, 0.85f, 0.95f, 1f), // jogador: ciano
        Color(1.00f, 0.42f, 0.38f, 1f), // coral
        Color(1.00f, 0.78f, 0.25f, 1f), // âmbar
        Color(0.72f, 0.48f, 1.00f, 1f), // violeta
    )

    val factionNames = arrayOf("Você", "Coral", "Âmbar", "Violeta")

    fun of(faction: Int): Color = if (faction == NO_FACTION) neutral else factions[faction % factions.size]
}
