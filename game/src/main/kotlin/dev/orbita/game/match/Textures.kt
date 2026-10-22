package dev.orbita.game.match

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable
import kotlin.math.hypot

/** Texturas geradas em código (sem assets externos no MVP). */
class Textures : Disposable {
    /** Brilho radial suave (halos e unidades). */
    val glow: Texture = radial(64) { d -> (1f - d).coerceIn(0f, 1f).let { it * it } }

    /** Disco sólido com borda suavizada. */
    val disc: Texture = radial(128) { d -> ((1f - d) * 40f).coerceIn(0f, 1f) }

    /** Anel fino. */
    val ring: Texture = radial(128) { d -> (1f - kotlin.math.abs(d - 0.92f) * 30f).coerceIn(0f, 1f) }

    private fun radial(size: Int, alpha: (Float) -> Float): Texture {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val c = (size - 1) / 2f
        for (py in 0 until size) for (px in 0 until size) {
            val d = hypot(px - c, py - c) / c
            pixmap.drawPixel(px, py, Color.rgba8888(1f, 1f, 1f, alpha(d)))
        }
        return Texture(pixmap, true).also {
            it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
            pixmap.dispose()
        }
    }

    override fun dispose() {
        glow.dispose()
        disc.dispose()
        ring.dispose()
    }
}
