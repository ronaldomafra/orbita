package dev.orbita.game.match

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import dev.orbita.sim.NO_FACTION
import dev.orbita.sim.Planet
import dev.orbita.sim.Rules
import dev.orbita.sim.SendUnits
import dev.orbita.sim.Simulation
import dev.orbita.sim.StandardMaps
import dev.orbita.sim.Target
import ktx.app.KtxScreen
import ktx.app.clearScreen
import kotlin.math.sqrt

/**
 * Partida single player (F6.3): o jogador é a facção 0. Os bots ainda não agem (F6.4).
 * A simulação avança em passo fixo; a renderização interpola entre o tick anterior e o atual.
 */
class MatchScreen(private val seed: Long = System.nanoTime()) : KtxScreen, MatchInput.Listener {

    private var sim = newSimulation()

    private val camera = OrthographicCamera()
    private val viewport = FitViewport(sim.map.width, sim.map.height, camera)
    private val hudViewport = ScreenViewport()
    private val batch = SpriteBatch(8191)
    private val shapes = ShapeRenderer()
    private val textures = Textures()
    private val font = BitmapFont().apply { data.setScale(1.6f) }
    private val tint = Color()

    // Posições do tick anterior, para interpolação.
    private var prevX = FloatArray(0)
    private var prevY = FloatArray(0)
    private var prevAlive = BooleanArray(0)
    private var accumulator = 0f

    // Seleção (círculo em coordenadas de mundo) e pré-visualização do laço.
    private var selectionActive = false
    private var selX = 0f
    private var selY = 0f
    private var selR = 0f
    private var lassoActive = false
    private var lassoX = 0f
    private var lassoY = 0f
    private var lassoR = 0f

    // Feedback visual da última ordem.
    private var orderFxTime = 0f
    private var orderFxX = 0f
    private var orderFxY = 0f

    private var statFrames = 0
    private var statElapsed = 0f
    private var fps = 0

    init {
        Gdx.input.inputProcessor = MatchInput(viewport, camera, sim.map.width, sim.map.height, this)
    }

    private fun newSimulation() = Simulation(StandardMaps.quad4(), seed)

    // ---- ciclo ----

    override fun render(delta: Float) {
        accumulator += minOf(delta, MAX_FRAME_DELTA)
        while (accumulator >= STEP) {
            snapshotPrevious()
            sim.step()
            accumulator -= STEP
        }
        val alpha = accumulator / STEP
        if (orderFxTime > 0f) orderFxTime -= delta

        clearScreen(Palette.background.r, Palette.background.g, Palette.background.b)
        viewport.apply()
        camera.update()
        drawWorld(alpha)
        drawHud()
        updateStats(delta)
    }

    private fun snapshotPrevious() {
        val u = sim.units
        if (prevX.size < u.capacity) {
            prevX = prevX.copyOf(u.capacity)
            prevY = prevY.copyOf(u.capacity)
            prevAlive = prevAlive.copyOf(u.capacity)
        }
        System.arraycopy(u.x, 0, prevX, 0, u.highWater)
        System.arraycopy(u.y, 0, prevY, 0, u.highWater)
        System.arraycopy(u.alive, 0, prevAlive, 0, u.highWater)
    }

    // ---- desenho ----

    private fun drawWorld(alpha: Float) {
        batch.projectionMatrix = camera.combined
        batch.begin()

        // Halos (aditivo) e discos dos planetas.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (p in sim.planets) {
            val r = sim.config.planetRadius(p.level)
            batch.setColor(Palette.of(p.owner).withAlpha(if (p.level >= 1) 0.28f else 0.07f))
            draw(textures.glow, p.x, p.y, r * 4.2f)
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        for (p in sim.planets) {
            val r = sim.config.planetRadius(p.level)
            val base = Palette.of(p.owner)
            val k = if (p.level >= 1) 0.55f else 0.22f
            batch.setColor(base.r * k, base.g * k, base.b * k, 1f)
            draw(textures.disc, p.x, p.y, r * 2f)
            // Anéis de nível: máximo possível (fraco) × atingido (forte).
            for (lv in 1..p.maxLevel) {
                batch.setColor(base.withAlpha(if (lv <= p.level) 0.9f else 0.15f))
                draw(textures.ring, p.x, p.y, (r + 4f + lv * 6f) * 2f)
            }
        }

        // Unidades (aditivo), interpoladas.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        val u = sim.units
        val sel2 = selR * selR
        for (i in 0 until u.highWater) {
            if (!u.alive[i]) continue
            val x = if (i < prevAlive.size && prevAlive[i]) prevX[i] + (u.x[i] - prevX[i]) * alpha else u.x[i]
            val y = if (i < prevAlive.size && prevAlive[i]) prevY[i] + (u.y[i] - prevY[i]) * alpha else u.y[i]
            val size = UNIT_SIZE * sqrt(u.power[i].toFloat())
            batch.color = Palette.of(u.owner[i])
            draw(textures.glow, x, y, size)
            if (selectionActive && u.owner[i] == PLAYER) {
                val dx = u.x[i] - selX
                val dy = u.y[i] - selY
                if (dx * dx + dy * dy <= sel2) {
                    batch.setColor(1f, 1f, 1f, 0.9f)
                    draw(textures.glow, x, y, size * 0.6f)
                }
            }
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.end()

        drawOverlays()
    }

    private fun drawOverlays() {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Line)
        for (p in sim.planets) {
            val r = sim.config.planetRadius(p.level)
            val c = Palette.of(p.owner)
            if (p.level >= 1) {
                val max = Rules.maxHealth(p.level)
                if (p.health < max) {
                    shapes.color = c
                    arc(p.x, p.y, r + 2f, p.health / max.toFloat())
                }
            }
            if (p.growth > 0) {
                shapes.color = if (p.level == 0) c else Color.WHITE
                arc(p.x, p.y, r + 10f + p.maxLevel * 6f, p.growth / Rules.growthCost(p.level).toFloat())
            }
        }
        if (lassoActive) {
            shapes.setColor(1f, 1f, 1f, 0.6f)
            shapes.circle(lassoX, lassoY, lassoR, 64)
        } else if (selectionActive) {
            shapes.color = Palette.of(PLAYER).withAlpha(0.5f)
            shapes.circle(selX, selY, selR, 64)
        }
        if (orderFxTime > 0f) {
            val t = 1f - orderFxTime / ORDER_FX_SECONDS
            shapes.setColor(1f, 1f, 1f, 1f - t)
            shapes.circle(orderFxX, orderFxY, 10f + 40f * t, 32)
        }
        shapes.end()
    }

    private fun arc(x: Float, y: Float, radius: Float, fraction: Float) {
        if (fraction <= 0f) return
        shapes.arc(x, y, radius, 90f, -360f * fraction.coerceAtMost(1f), 48)
    }

    private fun drawHud() {
        hudViewport.apply(true)
        batch.projectionMatrix = hudViewport.camera.combined
        batch.begin()
        val top = hudViewport.worldHeight - 16f
        var x = 16f
        for (f in sim.factions) {
            font.color = if (f.eliminated) Color.DARK_GRAY else Palette.of(f.id)
            val text = "${Palette.factionNames[f.id]} ${sim.totalPower(f.id)}"
            x += font.draw(batch, text, x, top).width + 28f
        }
        font.color = Color.GRAY
        font.draw(batch, "$fps fps", hudViewport.worldWidth - 120f, top)

        val result = when {
            sim.factions[PLAYER].eliminated -> "Derrota — toque para recomeçar"
            sim.winner == PLAYER -> "Vitória! — toque para recomeçar"
            else -> null
        }
        if (result != null) {
            font.color = Color.WHITE
            font.draw(batch, result, 0f, hudViewport.worldHeight / 2, hudViewport.worldWidth, 1, false)
        }
        batch.end()
    }

    private fun draw(texture: com.badlogic.gdx.graphics.Texture, cx: Float, cy: Float, size: Float) {
        batch.draw(texture, cx - size / 2, cy - size / 2, size, size)
    }

    private fun Color.withAlpha(a: Float): Color = tint.set(r, g, b, a)

    private fun updateStats(delta: Float) {
        statFrames++
        statElapsed += delta
        if (statElapsed >= 1f) {
            fps = statFrames
            Gdx.app.log(LOG_TAG, "fps=$fps tick=${sim.tick} units=${sim.units.highWater}")
            statFrames = 0
            statElapsed = 0f
        }
    }

    // ---- entrada ----

    override fun onLasso(centerX: Float, centerY: Float, radius: Float, finished: Boolean) {
        if (isGameOver()) return
        if (finished) {
            lassoActive = false
            selectionActive = radius > MIN_SELECTION_RADIUS
            selX = centerX; selY = centerY; selR = radius
        } else {
            lassoActive = true
            selectionActive = false
            lassoX = centerX; lassoY = centerY; lassoR = radius
        }
    }

    override fun onLassoCancel() {
        lassoActive = false
    }

    override fun onTap(worldX: Float, worldY: Float, doubleTap: Boolean) {
        if (isGameOver()) {
            restart()
            return
        }
        val planet = planetAt(worldX, worldY)
        if (selectionActive) {
            val target = if (planet != null) Target.PlanetRef(planet.id) else Target.Point(worldX, worldY)
            sim.submit(SendUnits(PLAYER, selX, selY, selR, target))
            orderFxTime = ORDER_FX_SECONDS
            orderFxX = planet?.x ?: worldX
            orderFxY = planet?.y ?: worldY
            selectionActive = false
        } else if (doubleTap && planet != null && planet.owner == PLAYER) {
            selectionActive = true
            selX = planet.x; selY = planet.y
            selR = sim.config.planetRadius(planet.level) + sim.config.orbitGap + sim.config.orbitJitter + 12f
        }
    }

    private fun planetAt(x: Float, y: Float): Planet? {
        var best: Planet? = null
        var bestD = Float.MAX_VALUE
        for (p in sim.planets) {
            val reach = sim.config.planetRadius(p.level) + TAP_MARGIN
            val dx = p.x - x
            val dy = p.y - y
            val d = dx * dx + dy * dy
            if (d <= reach * reach && d < bestD) { best = p; bestD = d }
        }
        return best
    }

    private fun isGameOver() = sim.winner != NO_FACTION || sim.factions[PLAYER].eliminated

    private fun restart() {
        sim = newSimulation()
        prevX = FloatArray(0); prevY = FloatArray(0); prevAlive = BooleanArray(0)
        selectionActive = false
        lassoActive = false
        accumulator = 0f
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        hudViewport.update(width, height, true)
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        textures.dispose()
        font.dispose()
    }

    companion object {
        const val LOG_TAG = "ORBITA"
        const val PLAYER = 0
        private const val STEP = 1f / 60f
        private const val MAX_FRAME_DELTA = 0.25f
        private const val UNIT_SIZE = 9f
        private const val TAP_MARGIN = 30f
        private const val MIN_SELECTION_RADIUS = 12f
        private const val ORDER_FX_SECONDS = 0.5f
    }
}
