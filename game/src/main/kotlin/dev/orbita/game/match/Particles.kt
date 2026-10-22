package dev.orbita.game.match

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.MathUtils

/** Pool fixo de partículas visuais (faíscas e anéis). Não interfere na simulação. */
class Particles(private val textures: Textures, private val capacity: Int = 1024) {

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
    private val isRing = BooleanArray(capacity)
    private var next = 0

    fun spark(px: Float, py: Float, color: Color, count: Int) {
        repeat(count) {
            val a = MathUtils.random(MathUtils.PI2)
            val speed = MathUtils.random(40f, 140f)
            emit(px, py, MathUtils.cos(a) * speed, MathUtils.sin(a) * speed, MathUtils.random(0.2f, 0.45f), 7f, -8f, color, false)
        }
    }

    fun ring(px: Float, py: Float, color: Color, startSize: Float, endSize: Float, seconds: Float) {
        emit(px, py, 0f, 0f, seconds, startSize, (endSize - startSize) / seconds, color, true)
    }

    fun flash(px: Float, py: Float, color: Color, sizePx: Float, seconds: Float) {
        emit(px, py, 0f, 0f, seconds, sizePx, -sizePx / seconds * 0.5f, color, false)
    }

    private fun emit(px: Float, py: Float, pvx: Float, pvy: Float, seconds: Float, s: Float, ds: Float, c: Color, ring: Boolean) {
        val i = next
        next = (next + 1) % capacity
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = seconds; maxLife[i] = seconds
        size[i] = s; growth[i] = ds
        r[i] = c.r; g[i] = c.g; b[i] = c.b
        isRing[i] = ring
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

    /** Desenha com o blending aditivo já configurado no [batch]. */
    fun draw(batch: SpriteBatch) {
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            val t = life[i] / maxLife[i]
            batch.setColor(r[i], g[i], b[i], t)
            val tex = if (isRing[i]) textures.ring else textures.spark
            val s = size[i]
            batch.draw(tex, x[i] - s / 2, y[i] - s / 2, s, s)
        }
    }
}
