package dev.orbita.game.match

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils

/** Pool fixo de partículas visuais (faíscas e anéis). Não interfere na simulação. */
class Particles(private val textures: Textures, private val capacity: Int = 2048) {

    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val growth = FloatArray(capacity)
    private val r = FloatArray(capacity)
    private val g = FloatArray(capacity)
    private val b = FloatArray(capacity)
    private val kind = IntArray(capacity)
    private var next = 0

    fun spark(px: Float, py: Float, color: Color, count: Int) {
        repeat(count) {
            val a = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(40f, 140f)
            emit(px, py, MathUtils.cos(a) * speed, MathUtils.sin(a) * speed, MathUtils.random(0.2f, 0.45f), 7f, -8f, color, KIND_SPARK)
        }
    }

    fun ring(px: Float, py: Float, color: Color, startSize: Float, endSize: Float, seconds: Float) {
        emit(px, py, 0f, 0f, seconds, startSize, (endSize - startSize) / seconds, color, KIND_RING)
    }

    fun flash(px: Float, py: Float, color: Color, sizePx: Float, seconds: Float) {
        emit(px, py, 0f, 0f, seconds, sizePx, -sizePx / seconds * 0.5f, color, KIND_GLOW)
    }

    /**
     * Explosão de uma unidade abatida (AJ-08): clarão branco quente + halo da facção.
     * Em batalhas em massa os flashes se somam (blending aditivo) e viram um clarão grande.
     */
    fun death(px: Float, py: Float, faction: Color, power: Int) {
        val k = kotlin.math.sqrt(power.toFloat())
        emitGlow(px, py, 0.4f, 34f * k, faction)
        emit(px, py, 0f, 0f, 0.28f, 22f * k, 20f, WARM_WHITE, KIND_SPARK)
    }

    private fun emitGlow(px: Float, py: Float, seconds: Float, s: Float, c: Color) {
        emit(px, py, 0f, 0f, seconds, s, s * 0.6f, c, KIND_GLOW)
    }

    private companion object {
        val WARM_WHITE = Color(1f, 0.95f, 0.78f, 1f)
        const val KIND_SPARK = 0
        const val KIND_RING = 1
        const val KIND_GLOW = 2
    }

    private fun emit(px: Float, py: Float, pvx: Float, pvy: Float, seconds: Float, s: Float, ds: Float, c: Color, k: Int) {
        val i = next
        next = (next + 1) % capacity
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = seconds; maxLife[i] = seconds
        size[i] = s; growth[i] = ds
        r[i] = c.r; g[i] = c.g; b[i] = c.b
        kind[i] = k
    }

    fun update(delta: Float) {
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            life[i] -= delta
            x[i] += vx[i] * delta
            y[i] += vy[i] * delta
            vx[i] *= 0.92f
            vy[i] *= 0.92f
            size[i] = (size[i] + growth[i] * delta).coerceAtLeast(0f)
        }
    }

    /** Desenha em espaço de tela (projeção de [rig]), com o blending aditivo já configurado no [batch]. */
    fun draw(batch: SpriteBatch, rig: CameraRig) {
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            if (!rig.project(x[i], y[i])) continue
            val t = life[i] / maxLife[i]
            batch.setColor(r[i], g[i], b[i], t)
            val tex = when (kind[i]) {
                KIND_RING -> textures.ring
                KIND_GLOW -> textures.glow
                else -> textures.spark
            }
            val s = size[i] * rig.pixelsPerUnit
            batch.draw(tex, rig.screenX - s / 2, rig.screenY - s / 2, s, s)
        }
    }
}
