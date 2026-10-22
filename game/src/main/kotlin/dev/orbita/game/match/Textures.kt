package dev.orbita.game.match

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Texturas geradas em código, todas num **único atlas**: o SpriteBatch descarrega o lote a cada troca
 * de textura, e alternar texturas por unidade derrubava o fps no A10 (~900 draw calls por frame).
 */
class Textures : Disposable {

    private val atlas: Texture

    /** Esfera iluminada de cima/esquerda, em tons de cinza (tingida pela cor do dono). */
    val sphere: TextureRegion

    /** Anel fino com borda suave. */
    val ring: TextureRegion

    /** Brilho radial suave (halos, coroas, unidades). */
    val glow: TextureRegion

    /** Brilho concentrado: núcleo intenso com queda rápida (pontos de luz). */
    val spark: TextureRegion

    /** Brilho gaussiano bem suave (coroas dos planetas). */
    val softGlow: TextureRegion

    init {
        val pixmap = Pixmap(ATLAS_W, ATLAS_H, Pixmap.Format.RGBA8888).apply { blending = Pixmap.Blending.None }
        paint(pixmap, 0, 0, 128) { nx, ny -> sphereTexel(nx, ny) }
        paint(pixmap, 128 + PAD, 0, 128) { nx, ny -> radialTexel(nx, ny) { d -> (1f - abs(d - 0.92f) * 22f).coerceIn(0f, 1f) } }
        paint(pixmap, 256 + 2 * PAD, 0, 64) { nx, ny -> radialTexel(nx, ny) { d -> (1f - d).coerceIn(0f, 1f).let { it * it } } }
        paint(pixmap, 256 + 2 * PAD, 64 + PAD, 32) { nx, ny -> radialTexel(nx, ny) { d -> (1f - d).coerceIn(0f, 1f).pow(3) } }
        paint(pixmap, 320 + 3 * PAD, 0, 128) { nx, ny -> radialTexel(nx, ny) { d -> (kotlin.math.exp(-d * d * 4.5f) * (1f - d)).coerceIn(0f, 1f) } }
        atlas = Texture(pixmap).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        pixmap.dispose()

        sphere = TextureRegion(atlas, 0, 0, 128, 128)
        ring = TextureRegion(atlas, 128 + PAD, 0, 128, 128)
        glow = TextureRegion(atlas, 256 + 2 * PAD, 0, 64, 64)
        spark = TextureRegion(atlas, 256 + 2 * PAD, 64 + PAD, 32, 32)
        softGlow = TextureRegion(atlas, 320 + 3 * PAD, 0, 128, 128)
    }

    /** Preenche um quadrado [size]×[size] em (ox, oy) com `texel(nx, ny)` em coordenadas normalizadas [-1, 1]. */
    private fun paint(pixmap: Pixmap, ox: Int, oy: Int, size: Int, texel: (Float, Float) -> Int) {
        val c = (size - 1) / 2f
        for (py in 0 until size) for (px in 0 until size) {
            pixmap.drawPixel(ox + px, oy + py, texel((px - c) / c, (py - c) / c))
        }
    }

    private inline fun radialTexel(nx: Float, ny: Float, alpha: (Float) -> Float): Int =
        Color.rgba8888(1f, 1f, 1f, alpha(hypot(nx, ny)))

    private fun sphereTexel(nx: Float, ny: Float): Int {
        val r2 = nx * nx + ny * ny
        if (r2 > 1f) return 0
        // Luz vinda de cima à esquerda (coordenadas da Pixmap: y para baixo).
        val nz = sqrt(1f - r2)
        val diffuse = max(0f, nx * -0.45f + ny * -0.55f + nz * 0.70f)
        val rim = (1f - nz).pow(2) * 0.55f            // borda acesa (atmosfera)
        val spec = diffuse.pow(18) * 0.6f            // reflexo
        val v = (0.28f + 0.72f * diffuse + rim + spec).coerceAtMost(1f)
        val edge = ((1f - sqrt(r2)) * 42f).coerceIn(0f, 1f) // antisserrilhado
        return Color.rgba8888(v, v, v, edge)
    }

    override fun dispose() {
        atlas.dispose()
    }

    private companion object {
        const val PAD = 2
        const val ATLAS_W = 512
        const val ATLAS_H = 128
    }
}
