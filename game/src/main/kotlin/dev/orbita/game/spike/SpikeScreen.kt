package dev.orbita.game.spike

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import ktx.app.KtxScreen
import ktx.app.clearScreen

/** Tela do spike: desenha [SpikeSim] em lote com brilho aditivo e mede fps e custo de simulação. */
class SpikeScreen(units: Int) : KtxScreen {

    private val sim = SpikeSim(units, seed = 42L)
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(SpikeSim.WORLD_W, SpikeSim.WORLD_H, camera)
    private val batch = SpriteBatch(8191)
    private val glow = createGlowTexture(32)
    private val font = BitmapFont()
    private val touch = Vector2()

    private val factionColors = arrayOf(
        Color(0.25f, 0.85f, 0.95f, 0.9f),
        Color(1.0f, 0.45f, 0.40f, 0.9f),
        Color(1.0f, 0.80f, 0.25f, 0.9f),
        Color(0.70f, 0.45f, 1.0f, 0.9f),
    )

    private var accumulator = 0f
    private var statFrames = 0
    private var statTicks = 0
    private var statSimNanos = 0L
    private var statRenderNanos = 0L
    private var statElapsed = 0f
    private var hud = ""

    private var dragFrom = -1

    init {
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
                dragFrom = sim.nearestAnchor(touch.x, touch.y)
                return true
            }

            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                viewport.unproject(touch.set(screenX.toFloat(), screenY.toFloat()))
                if (dragFrom >= 0) sim.send(dragFrom, sim.nearestAnchor(touch.x, touch.y))
                dragFrom = -1
                return true
            }
        }
    }

    override fun render(delta: Float) {
        accumulator += minOf(delta, MAX_FRAME_DELTA)
        val simStart = System.nanoTime()
        while (accumulator >= STEP) {
            sim.step()
            accumulator -= STEP
            statTicks++
        }
        statSimNanos += System.nanoTime() - simStart

        val renderStart = System.nanoTime()
        clearScreen(0.02f, 0.02f, 0.05f)
        viewport.apply()
        batch.projectionMatrix = camera.combined
        batch.begin()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        batch.setColor(0.5f, 0.55f, 0.7f, 0.5f)
        for (a in 0 until SpikeSim.ANCHORS) {
            batch.draw(glow, sim.anchorX[a] - ANCHOR_SIZE / 2, sim.anchorY[a] - ANCHOR_SIZE / 2, ANCHOR_SIZE, ANCHOR_SIZE)
        }
        val half = UNIT_SIZE / 2
        for (i in 0 until sim.n) {
            batch.color = factionColors[sim.faction[i]]
            batch.draw(glow, sim.x[i] - half, sim.y[i] - half, UNIT_SIZE, UNIT_SIZE)
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.color = Color.WHITE
        font.draw(batch, hud, 12f, SpikeSim.WORLD_H - 12f)
        batch.end()
        statRenderNanos += System.nanoTime() - renderStart

        statFrames++
        statElapsed += delta
        if (statElapsed >= 1f) publishStats()
    }

    private fun publishStats() {
        val simMsPerTick = if (statTicks > 0) statSimNanos / 1e6 / statTicks else 0.0
        val renderMs = statRenderNanos / 1e6 / statFrames
        val heapKb = Gdx.app.javaHeap / 1024
        hud = "units=%d fps=%d sim=%.2fms/tick renderCpu=%.2fms heap=%dKB"
            .format(sim.n, statFrames, simMsPerTick, renderMs, heapKb)
        Gdx.app.log(LOG_TAG, hud)
        statFrames = 0; statTicks = 0; statSimNanos = 0; statRenderNanos = 0; statElapsed = 0f
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun dispose() {
        batch.dispose()
        glow.dispose()
        font.dispose()
    }

    companion object {
        const val LOG_TAG = "ORBITA_SPIKE"
        private const val STEP = 1f / 60f
        private const val MAX_FRAME_DELTA = 0.25f
        private const val UNIT_SIZE = 9f
        private const val ANCHOR_SIZE = 70f

        private fun createGlowTexture(size: Int): Texture {
            val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
            val c = (size - 1) / 2f
            for (py in 0 until size) for (px in 0 until size) {
                val d = Math.hypot((px - c).toDouble(), (py - c).toDouble()).toFloat() / c
                val a = (1f - d).coerceIn(0f, 1f)
                pixmap.drawPixel(px, py, Color.rgba8888(1f, 1f, 1f, a * a))
            }
            return Texture(pixmap, true).also {
                it.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                pixmap.dispose()
            }
        }
    }
}
