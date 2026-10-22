package dev.orbita.game.match

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import java.util.Random
import kotlin.math.exp
import kotlin.math.sin

/** Tema de fundo de uma fase: cor base e as cores das nuvens. */
data class NebulaTheme(val seed: Long, val base: Color, val clouds: List<Color>, val intensity: Float = 1f) {
    companion object {
        val DEEP_VIOLET = NebulaTheme(
            seed = 7,
            base = Color(0.010f, 0.012f, 0.032f, 1f),
            clouds = listOf(Color(0.30f, 0.12f, 0.55f, 1f), Color(0.08f, 0.22f, 0.50f, 1f), Color(0.45f, 0.10f, 0.35f, 1f)),
        )
        val TEAL_DRIFT = NebulaTheme(
            seed = 11,
            base = Color(0.006f, 0.016f, 0.022f, 1f),
            clouds = listOf(Color(0.05f, 0.35f, 0.40f, 1f), Color(0.10f, 0.18f, 0.45f, 1f), Color(0.20f, 0.40f, 0.25f, 1f)),
        )
        val EMBER = NebulaTheme(
            seed = 23,
            base = Color(0.020f, 0.010f, 0.012f, 1f),
            clouds = listOf(Color(0.50f, 0.15f, 0.10f, 1f), Color(0.45f, 0.30f, 0.08f, 1f), Color(0.30f, 0.08f, 0.30f, 1f)),
        )
    }
}

/**
 * Gera a nebulosa uma vez numa textura pequena (esticada para o mapa com filtro linear).
 * Custa 1 quad por frame, em vez de vários quads aditivos grandes.
 */
object Nebula {
    private const val W = 320
    private const val H = 152

    fun render(theme: NebulaTheme): Texture {
        val rnd = Random(theme.seed)
        // Nuvens: gaussianas alongadas, em posições aleatórias.
        val blobs = List(14) {
            Blob(
                x = rnd.nextFloat() * W, y = rnd.nextFloat() * H,
                rx = 40f + rnd.nextFloat() * 110f, ry = 25f + rnd.nextFloat() * 60f,
                weight = 0.25f + rnd.nextFloat() * 0.75f,
                color = theme.clouds[rnd.nextInt(theme.clouds.size)],
            )
        }
        val pixmap = Pixmap(W, H, Pixmap.Format.RGBA8888).apply { blending = Pixmap.Blending.None }
        for (py in 0 until H) for (px in 0 until W) {
            var r = theme.base.r; var g = theme.base.g; var b = theme.base.b
            // Ondulação barata para quebrar a regularidade das gaussianas.
            val wobble = 0.75f + 0.25f * sin(px * 0.045f + sin(py * 0.07f) * 2.3f) * sin(py * 0.05f + px * 0.013f)
            for (blob in blobs) {
                val dx = (px - blob.x) / blob.rx
                val dy = (py - blob.y) / blob.ry
                val k = exp(-(dx * dx + dy * dy)) * blob.weight * wobble * 0.22f * theme.intensity
                r += blob.color.r * k; g += blob.color.g * k; b += blob.color.b * k
            }
            pixmap.drawPixel(px, py, Color.rgba8888(r.coerceAtMost(1f), g.coerceAtMost(1f), b.coerceAtMost(1f), 1f))
        }
        return Texture(pixmap).also {
            it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            pixmap.dispose()
        }
    }

    private class Blob(val x: Float, val y: Float, val rx: Float, val ry: Float, val weight: Float, val color: Color)
}
