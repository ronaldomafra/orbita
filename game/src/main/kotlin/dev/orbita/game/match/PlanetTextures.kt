package dev.orbita.game.match

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Superfícies de planeta procedurais (AJ-17), em tons de cinza para serem tingidas pela cor do dono.
 * Ruído 3D sobre o ponto da esfera (sem costura) + sombreamento; todas num único atlas (1 troca de textura).
 */
class PlanetTextures : Disposable {

    private val atlas: Texture
    val variants: Array<TextureRegion>

    init {
        val pixmap = Pixmap(SIZE * VARIANTS + PAD * (VARIANTS - 1), SIZE, Pixmap.Format.RGBA8888)
            .apply { blending = Pixmap.Blending.None }
        for (v in 0 until VARIANTS) paintPlanet(pixmap, v * (SIZE + PAD), v)
        atlas = Texture(pixmap).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        pixmap.dispose()
        variants = Array(VARIANTS) { TextureRegion(atlas, it * (SIZE + PAD), 0, SIZE, SIZE) }
    }

    private fun paintPlanet(pixmap: Pixmap, ox: Int, variant: Int) {
        val c = (SIZE - 1) / 2f
        for (py in 0 until SIZE) for (px in 0 until SIZE) {
            val nx = (px - c) / c
            val ny = (py - c) / c
            val r2 = nx * nx + ny * ny
            if (r2 > 1f) { pixmap.drawPixel(ox + px, py, 0); continue }
            val nz = sqrt(1f - r2)
            val surface = surface(variant, nx, ny, nz)
            // Luz de cima/esquerda (y da Pixmap para baixo), borda acesa e reflexo suave.
            val diffuse = max(0f, nx * -0.45f + ny * -0.55f + nz * 0.70f)
            val rim = (1f - nz).pow(2.2f) * 0.45f
            val spec = diffuse.pow(24) * 0.35f
            val v = (surface * (0.30f + 0.70f * diffuse) + rim + spec).coerceIn(0f, 1f)
            val edge = ((1f - sqrt(r2)) * 42f).coerceIn(0f, 1f)
            pixmap.drawPixel(ox + px, py, Color.rgba8888(v, v, v, edge))
        }
    }

    /** Brilho da superfície em [0.45, 1] por variante: manchas oceânicas, veios marmorizados, lava. */
    private fun surface(variant: Int, x: Float, y: Float, z: Float): Float = when (variant) {
        0 -> {
            // Oceano com nuvens: manchas claras de bordas suaves.
            val n = fbm(x * 2.2f, y * 2.2f, z * 2.2f + 3f, 5)
            0.55f + 0.45f * smooth(0.48f, 0.62f, n)
        }
        1 -> {
            // Veios marmorizados: seno distorcido pelo ruído (domain warp).
            val w = fbm(x * 1.8f, y * 1.8f, z * 1.8f, 5)
            val veins = 0.5f + 0.5f * sin((y * 4.5f + w * 7f).toDouble()).toFloat()
            0.5f + 0.5f * veins.pow(1.6f)
        }
        else -> {
            // Lava/rocha: ruído "ridged" com fissuras claras.
            val n = fbm(x * 2.8f + 7f, y * 2.8f, z * 2.8f, 5)
            val ridge = 1f - abs(n * 2f - 1f)
            0.45f + 0.55f * ridge.pow(3f)
        }
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun fbm(x: Float, y: Float, z: Float, octaves: Int): Float {
        var sum = 0f
        var amp = 0.5f
        var freq = 1f
        var norm = 0f
        repeat(octaves) {
            sum += amp * valueNoise(x * freq, y * freq, z * freq)
            norm += amp
            amp *= 0.5f
            freq *= 2.03f
        }
        return sum / norm
    }

    private fun valueNoise(x: Float, y: Float, z: Float): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt(); val zi = floor(z).toInt()
        val xf = x - xi; val yf = y - yi; val zf = z - zi
        val u = fade(xf); val v = fade(yf); val w = fade(zf)
        fun h(i: Int, j: Int, k: Int) = hash(xi + i, yi + j, zi + k)
        val x00 = lerp(h(0, 0, 0), h(1, 0, 0), u)
        val x10 = lerp(h(0, 1, 0), h(1, 1, 0), u)
        val x01 = lerp(h(0, 0, 1), h(1, 0, 1), u)
        val x11 = lerp(h(0, 1, 1), h(1, 1, 1), u)
        return lerp(lerp(x00, x10, v), lerp(x01, x11, v), w)
    }

    private fun fade(t: Float) = t * t * (3f - 2f * t)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun hash(x: Int, y: Int, z: Int): Float {
        var h = x * 374761393 + y * 668265263 + z * 1274126177
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    override fun dispose() = atlas.dispose()

    companion object {
        const val VARIANTS = 3
        private const val SIZE = 128
        private const val PAD = 2
    }
}
