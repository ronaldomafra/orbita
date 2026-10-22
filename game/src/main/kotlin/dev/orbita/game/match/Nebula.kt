package dev.orbita.game.match

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import java.util.Random
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/** Nebulosa localizada em forma de anel (AJ-15): miolo escuro, borda, névoa externa. */
data class NebulaCloud(
    val x: Float,
    val y: Float,
    val size: Float,
    val seed: Long,
    val core: Color,
    val rim: Color,
    val haze: Color,
)

/** Cenário de fundo de uma fase (AJ-13..15): céu preto, estrelas nítidas, poeira e nebulosas localizadas. */
data class NebulaTheme(
    val seed: Long,
    val clouds: List<NebulaCloud>,
    val starCount: Int = 1400,
    val dustPatches: Int = 4,
    /** Opacidade das nebulosas: o fundo deve ser discreto e não competir com as unidades. */
    val nebulaOpacity: Float = 0.28f,
) {
    companion object {
        val DEEP_VIOLET = NebulaTheme(
            seed = 7,
            clouds = listOf(
                NebulaCloud(
                    x = 470f, y = 300f, size = 430f, seed = 3,
                    core = Color(0.42f, 0.16f, 0.36f, 1f),
                    rim = Color(0.30f, 0.62f, 0.58f, 1f),
                    haze = Color(0.55f, 0.32f, 0.16f, 1f),
                ),
            ),
        )
        val TEAL_DRIFT = NebulaTheme(
            seed = 11,
            clouds = listOf(
                NebulaCloud(1150f, 480f, 520f, 5, Color(0.04f, 0.10f, 0.18f, 1f), Color(0.20f, 0.70f, 0.75f, 1f), Color(0.18f, 0.30f, 0.55f, 1f)),
            ),
        )
        val EMBER = NebulaTheme(
            seed = 23,
            clouds = listOf(
                NebulaCloud(800f, 380f, 600f, 9, Color(0.22f, 0.05f, 0.04f, 1f), Color(0.85f, 0.45f, 0.15f, 1f), Color(0.45f, 0.12f, 0.25f, 1f)),
            ),
        )
    }
}

/**
 * Desenha o cenário. Estrelas usam o atlas compartilhado ([Textures.dot]/[Textures.cross]);
 * nebulosas e poeira são pré-renderizadas uma vez em texturas pequenas (1 quad cada).
 */
class Backdrop(
    private val theme: NebulaTheme,
    private val textures: Textures,
    worldWidth: Float,
    worldHeight: Float,
) : Disposable {

    private val n = theme.starCount
    private val starX = FloatArray(n)
    private val starY = FloatArray(n)
    private val starSize = FloatArray(n)
    private val starR = FloatArray(n)
    private val starG = FloatArray(n)
    private val starB = FloatArray(n)
    private val starA = FloatArray(n)
    private val starCross = BooleanArray(n)

    private val cloudTextures = theme.clouds.map { renderCloud(it) }
    private val dustTexture = renderDust(theme.seed)
    private val dustX: FloatArray
    private val dustY: FloatArray
    private val dustSize: FloatArray

    init {
        val rnd = Random(theme.seed)
        // Área estendida: cobre a câmera afastada e o pan além das bordas (AJ-01/AJ-10).
        val minX = -worldWidth * 0.7f
        val minY = -worldHeight * 0.8f
        val spanX = worldWidth * 2.4f
        val spanY = worldHeight * 2.6f
        for (k in 0 until n) {
            starX[k] = minX + rnd.nextFloat() * spanX
            starY[k] = minY + rnd.nextFloat() * spanY
            val roll = rnd.nextFloat()
            when {
                roll < 0.55f -> setStarColor(k, 1f, 0.84f, 0.45f)  // dourada (maioria)
                roll < 0.80f -> setStarColor(k, 0.78f, 0.86f, 1f)  // branco-azulada
                roll < 0.90f -> setStarColor(k, 1f, 1f, 1f)
                roll < 0.95f -> setStarColor(k, 1f, 0.45f, 0.35f)  // avermelhada
                else -> setStarColor(k, 0.55f, 1f, 0.55f)          // esverdeada
            }
            // Maioria pequena e fraca; poucas maiores, com brilho contido (não competem com as unidades).
            val bright = rnd.nextFloat()
            starA[k] = 0.12f + bright * bright * bright * 0.43f
            starSize[k] = 1.6f + bright * bright * 2.2f
            starCross[k] = rnd.nextFloat() < 0.025f
            if (starCross[k]) { starA[k] = 0.5f; starSize[k] = 3f; setStarColor(k, 0.85f, 0.9f, 1f) }
        }
        dustX = FloatArray(theme.dustPatches) { minX + rnd.nextFloat() * spanX }
        dustY = FloatArray(theme.dustPatches) { minY + rnd.nextFloat() * spanY }
        dustSize = FloatArray(theme.dustPatches) { 450f + rnd.nextFloat() * 400f }
    }

    private fun setStarColor(k: Int, r: Float, g: Float, b: Float) {
        starR[k] = r; starG[k] = g; starB[k] = b
    }

    /** Chamar com o batch aberto e a projeção do mundo. Deixa o blending em modo normal. */
    fun draw(batch: SpriteBatch, time: Float) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(1f, 1f, 1f, 0.3f)
        for (k in dustX.indices) {
            val s = dustSize[k]
            batch.draw(dustTexture, dustX[k] - s / 2, dustY[k] - s / 2, s, s)
        }
        batch.setColor(1f, 1f, 1f, theme.nebulaOpacity)
        theme.clouds.forEachIndexed { i, c ->
            batch.draw(cloudTextures[i], c.x - c.size / 2, c.y - c.size / 2, c.size, c.size)
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (k in 0 until n) {
            var a = starA[k]
            if (starCross[k]) a *= 0.75f + 0.25f * MathUtils.sin(time * 1.1f + k)
            batch.setColor(starR[k], starG[k], starB[k], a)
            val s = starSize[k]
            batch.draw(textures.dot, starX[k] - s / 2, starY[k] - s / 2, s, s)
            if (starCross[k]) {
                val c = s * 2.6f
                batch.draw(textures.cross, starX[k] - c / 2, starY[k] - c / 2, c, c)
            }
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun renderCloud(cloud: NebulaCloud): Texture {
        val size = 256
        val rnd = Random(cloud.seed)
        val p1 = rnd.nextFloat() * 6.28f
        val p2 = rnd.nextFloat() * 6.28f
        val p3 = rnd.nextFloat() * 6.28f
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888).apply { blending = Pixmap.Blending.None }
        val c = (size - 1) / 2f
        for (py in 0 until size) for (px in 0 until size) {
            val nx = (px - c) / c
            val ny = (py - c) / c
            val angle = atan2(ny, nx)
            // Contorno irregular: raio deformado por senoides de frequências diferentes.
            val wobble = 1f + 0.16f * (0.5f * sin(3 * angle + p1) + 0.3f * sin(5 * angle + p2) + 0.2f * sin(9 * angle + p3))
            val d = hypot(nx, ny) / wobble
            // Perfil suave: miolo escuro e transparente, borda difusa, névoa larga por fora (REF-04).
            val core = (1f - smoothstep(0.30f, 0.62f, d)) * 0.8f
            val rim = exp(-((d - 0.60f) / 0.09f).let { it * it }) * 0.85f
            val haze = exp(-((d - 0.80f) / 0.20f).let { it * it }) * 0.85f
            val fade = 1f - smoothstep(0.82f, 1f, d)
            val grain = 0.7f + 0.6f * hash(px, py, cloud.seed)
            val r = (cloud.core.r * core + cloud.rim.r * rim + cloud.haze.r * haze) * grain
            val g = (cloud.core.g * core + cloud.rim.g * rim + cloud.haze.g * haze) * grain
            val b = (cloud.core.b * core + cloud.rim.b * rim + cloud.haze.b * haze) * grain
            val a = ((core * 0.55f + rim * 0.55f + haze * 0.45f) * fade).coerceIn(0f, 0.85f)
            pixmap.drawPixel(px, py, Color.rgba8888(r.coerceAtMost(1f), g.coerceAtMost(1f), b.coerceAtMost(1f), a))
        }
        return toTexture(pixmap)
    }

    /** Poeira: grãos marrom-avermelhados esparsos e quase invisíveis (AJ-14). */
    private fun renderDust(seed: Long): Texture {
        val size = 256
        val rnd = Random(seed * 31)
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888).apply { blending = Pixmap.Blending.None }
        pixmap.setColor(0f, 0f, 0f, 0f)
        pixmap.fill()
        val c = (size - 1) / 2f
        repeat(900) {
            val px = rnd.nextInt(size)
            val py = rnd.nextInt(size)
            val falloff = (1f - hypot(px - c, py - c) / c).coerceIn(0f, 1f)
            if (rnd.nextFloat() > falloff) return@repeat
            pixmap.drawPixel(px, py, Color.rgba8888(0.45f, 0.28f, 0.22f, 0.25f + rnd.nextFloat() * 0.35f))
        }
        return toTexture(pixmap)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun hash(x: Int, y: Int, seed: Long): Float {
        var h = x * 374761393 + y * 668265263 + seed.toInt() * 2147483647
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private fun toTexture(pixmap: Pixmap): Texture = Texture(pixmap).also {
        it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pixmap.dispose()
    }

    override fun dispose() {
        cloudTextures.forEach { it.dispose() }
        dustTexture.dispose()
    }
}
