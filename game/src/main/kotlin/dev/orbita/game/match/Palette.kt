package dev.orbita.game.match

import com.badlogic.gdx.graphics.Color
import dev.orbita.sim.NO_FACTION

/** Paleta própria do Órbita. A facção 0 é sempre o jogador. */
object Palette {
    val background = Color(0.008f, 0.010f, 0.028f, 1f)
    val neutral = Color(0.55f, 0.58f, 0.64f, 1f)

    private val factions = arrayOf(
        Color(0.20f, 0.90f, 1.00f, 1f), // jogador: ciano
        Color(1.00f, 0.36f, 0.32f, 1f), // coral
        Color(1.00f, 0.80f, 0.22f, 1f), // âmbar
        Color(0.74f, 0.45f, 1.00f, 1f), // violeta
    )

    val factionNames = arrayOf("Você", "Coral", "Âmbar", "Violeta")

    fun of(faction: Int): Color = if (faction == NO_FACTION) neutral else factions[faction % factions.size]
}
