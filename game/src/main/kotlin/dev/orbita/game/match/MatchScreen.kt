package dev.orbita.game.match

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import dev.orbita.ai.BotDriver
import dev.orbita.sim.NO_FACTION
import dev.orbita.sim.Planet
import dev.orbita.sim.Rules
import dev.orbita.sim.SendUnits
import dev.orbita.sim.SimEvents
import dev.orbita.sim.Simulation
import dev.orbita.sim.StandardMaps
import dev.orbita.sim.Target
import dev.orbita.sim.UnitStore
import ktx.app.KtxScreen
import ktx.app.clearScreen
import kotlin.math.sqrt

/**
 * Partida single player: o jogador é a facção 0 e as facções 1..3 são bots.
 * A simulação avança em passo fixo; a renderização interpola entre o tick anterior e o atual.
 * As órbitas são desenhadas em perspectiva (elipse inclinada); a simulação continua 2D.
 */
class MatchScreen(
    private val seed: Long = System.nanoTime(),
    private var debugAi: Boolean = false,
    /** > 0 substitui as unidades iniciais de cada base (teste de estresse). */
    private val startUnitsOverride: Int = 0,
    theme: NebulaTheme = NebulaTheme.DEEP_VIOLET,
) : KtxScreen, MatchInput.Listener {

    private var sim = newSimulation()
    private var bots = BotDriver.standard(sim, seed)

    private val camera = OrthographicCamera()
    private val viewport = FitViewport(sim.map.width, sim.map.height, camera)
    private val hudViewport = ScreenViewport()
    private val batch = SpriteBatch(8191)
    private val shapes = ShapeRenderer()
    private val textures = Textures()
    private val backdrop = Backdrop(theme, textures, sim.map.width, sim.map.height)
    private val font = BitmapFont().apply { data.setScale(1.6f) }
    private val tint = Color()
    private val particles = Particles(textures)
    private val input = MatchInput(viewport, camera, sim.map.width, sim.map.height, this)
    private var time = 0f

    // Posições do tick anterior, para interpolação e para a direção dos cometas.
    private var prevX = FloatArray(0)
    private var prevY = FloatArray(0)
    private var prevAlive = BooleanArray(0)
    private var accumulator = 0f

    // Posição de desenho de cada unidade (após interpolação e projeção da órbita).
    private var drawX = FloatArray(0)
    private var drawY = FloatArray(0)

    // Seleção (círculo em coordenadas de mundo) e pré-visualização do laço.
    private var selectionActive = false
    private var selX = 0f
    private var selY = 0f
    private var selR = 0f
    private var selectedPlanet = -1
    private var lassoActive = false
    private var lassoX = 0f
    private var lassoY = 0f
    private var lassoR = 0f

    // Feedback visual da última ordem.
    private var orderFxTime = 0f
    private var orderFxX = 0f
    private var orderFxY = 0f

    private var statFrames = 0
    private var statSimNanos = 0L
    private val hudText = StringBuilder(32)
    private var statElapsed = 0f
    private var fps = 0

    init {
        Gdx.input.inputProcessor = input
    }

    private fun newSimulation(): Simulation {
        val base = StandardMaps.quad4()
        val map = if (startUnitsOverride > 0) {
            base.copy(planets = base.planets.map { if (it.owner >= 0) it.copy(startUnits = startUnitsOverride) else it })
        } else base
        return Simulation(map, seed)
    }

    // ---- ciclo ----

    override fun render(delta: Float) {
        accumulator += minOf(delta, MAX_FRAME_DELTA)
        val simStart = System.nanoTime()
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) debugAi = !debugAi
        var steps = 0
        while (accumulator >= STEP && steps < MAX_STEPS_PER_FRAME) {
            steps++
            snapshotPrevious()
            bots.beforeStep()
            sim.step()
            consumeEvents()
            accumulator -= STEP
        }
        // Sob carga extrema o jogo desacelera em vez de congelar (espiral do passo fixo).
        if (accumulator >= STEP) accumulator = 0f
        statSimNanos += System.nanoTime() - simStart
        val alpha = accumulator / STEP
        time += delta
        particles.update(delta)
        input.update(delta)
        if (orderFxTime > 0f) orderFxTime -= delta

        clearScreen(0f, 0f, 0f)
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
        backdrop.draw(batch, time)
        computeDrawPositions(alpha)

        // Coroas (aditivo) só para planetas ativos; trilhas de órbita discretas (AJ-07/AJ-16).
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (p in sim.planets) {
            if (p.level < 1) continue
            val r = sim.config.planetRadius(p.level)
            val pulse = 1f + 0.05f * MathUtils.sin(time * 1.4f + p.id * 1.7f)
            batch.setColor(Palette.of(p.owner).withAlpha(0.32f))
            draw(textures.softGlow, p.x, p.y, r * 4.6f * pulse)
            val orbit = orbitRadius(p)
            batch.setColor(0.55f, 0.6f, 0.7f, 0.10f)
            drawEllipse(textures.ring, p.x, p.y, orbit - 6f, ORBIT_TILT)
            drawEllipse(textures.ring, p.x, p.y, orbit + 6f, ORBIT_TILT)
        }
        drawSelectionDisc()

        // Unidades atrás do planeta (metade de cima do anel inclinado).
        drawUnits(behind = true)

        // Esferas (normal) e borda acesa (aditivo).
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        for (p in sim.planets) {
            val r = sphereRadius(p)
            val c = Palette.of(p.owner)
            when {
                p.level >= 1 -> batch.setColor(lerp(c.r, 1f, 0.1f), lerp(c.g, 1f, 0.1f), lerp(c.b, 1f, 0.1f), 1f)
                p.owner != NO_FACTION -> batch.setColor(c.r * 0.4f + 0.2f, c.g * 0.4f + 0.2f, c.b * 0.4f + 0.2f, 1f)
                else -> batch.setColor(0.78f, 0.80f, 0.84f, 1f) // neutro: cinza claro, sem brilho (AJ-16)
            }
            draw(textures.sphere, p.x, p.y, r * 2f)
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (p in sim.planets) {
            if (p.level < 1) continue
            val r = sphereRadius(p)
            val c = Palette.of(p.owner)
            batch.setColor(c.withAlpha(0.55f))
            draw(textures.ring, p.x, p.y, r * 2.1f)
            // Pontos de nível abaixo do planeta: acesos até o nível atual.
            for (lv in 1..p.maxLevel) {
                batch.setColor(c.withAlpha(if (lv <= p.level) 1f else 0.25f))
                draw(textures.spark, p.x + (lv - (p.maxLevel + 1) / 2f) * 12f, p.y - orbitRadius(p) * ORBIT_TILT - 14f, 12f)
            }
        }

        // Unidades na frente do planeta, paradas e em trânsito (cometas).
        drawUnits(behind = false)

        particles.draw(batch)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.end()

        drawOverlays()
    }

    /** Interpola e projeta: unidades em órbita são desenhadas no anel inclinado (AJ-07). */
    private fun computeDrawPositions(alpha: Float) {
        val u = sim.units
        if (drawX.size < u.capacity) {
            drawX = drawX.copyOf(u.capacity)
            drawY = drawY.copyOf(u.capacity)
        }
        for (i in 0 until u.highWater) {
            if (!u.alive[i]) continue
            val interpolate = i < prevAlive.size && prevAlive[i]
            var x = if (interpolate) prevX[i] + (u.x[i] - prevX[i]) * alpha else u.x[i]
            var y = if (interpolate) prevY[i] + (u.y[i] - prevY[i]) * alpha else u.y[i]
            if (u.mode[i] == UnitStore.MODE_ORBIT && u.lock[i] < 0) {
                val p = sim.planets[u.home[i]]
                val ox = x - p.x
                val oy = (y - p.y) * ORBIT_TILT
                if (p.id == selectedPlanet && selectionActive) {
                    // "Átomo": dois planos inclinados que se cruzam quando o planeta está selecionado (AJ-07).
                    val c = ATOM_COS
                    val s = if (i and 1 == 0) ATOM_SIN else -ATOM_SIN
                    x = p.x + ox * c - oy * s
                    y = p.y + ox * s + oy * c
                } else {
                    x = p.x + ox
                    y = p.y + oy
                }
            }
            drawX[i] = x
            drawY[i] = y
        }
    }

    private fun drawUnits(behind: Boolean) {
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        val u = sim.units
        val sel2 = selR * selR
        for (i in 0 until u.highWater) {
            if (!u.alive[i]) continue
            val orbiting = u.mode[i] == UnitStore.MODE_ORBIT && u.lock[i] < 0
            // Atrás = metade de cima do anel em órbita (o "y" simulado acima do planeta).
            val isBehind = orbiting && u.y[i] > sim.planets[u.home[i]].y
            if (isBehind != behind) continue

            val x = drawX[i]
            val y = drawY[i]
            val scale = sqrt(u.power[i].toFloat())
            var selected = false
            if (selectionActive && u.owner[i] == PLAYER) {
                val dx = u.x[i] - selX
                val dy = u.y[i] - selY
                selected = dx * dx + dy * dy <= sel2
            }
            val c = Palette.of(u.owner[i])
            val dim = if (isBehind) 0.6f else 1f

            // Cometa: rastro alongado na direção do movimento (AJ-12).
            val moving = u.mode[i] == UnitStore.MODE_MOVE_POINT || u.mode[i] == UnitStore.MODE_MOVE_PLANET || u.lock[i] >= 0
            if (moving && i < prevAlive.size && prevAlive[i]) {
                val vx = u.x[i] - prevX[i]
                val vy = u.y[i] - prevY[i]
                val speed = sqrt(vx * vx + vy * vy)
                if (speed > 0.6f) {
                    val len = UNIT_SIZE * scale + speed * COMET_LENGTH
                    val width = UNIT_SIZE * scale * 0.55f
                    val angle = MathUtils.atan2(vy, vx) * MathUtils.radiansToDegrees
                    batch.setColor(c.r, c.g, c.b, 0.75f)
                    // A cabeça fica na posição da unidade; o rastro se estende para trás.
                    batch.draw(textures.glow, x - len + width / 2, y - width / 2, len - width / 2, width / 2, len, width, 1f, 1f, angle)
                }
            }
            if (selected) {
                batch.setColor(1f, 1f, 1f, 0.95f * dim) // unidades selecionadas ficam brancas (AJ-06)
                draw(textures.glow, x, y, UNIT_SIZE * scale * 1.15f)
            } else {
                batch.setColor(c.r, c.g, c.b, 0.85f * dim)
                draw(textures.glow, x, y, UNIT_SIZE * scale)
            }
            batch.setColor(1f, 1f, 1f, 0.8f * dim)
            draw(textures.spark, x, y, UNIT_CORE * scale)
        }
    }

    /** Seleção: disco translúcido na cor do jogador com aro aceso (AJ-06). Também usado no laço. */
    private fun drawSelectionDisc() {
        val active = lassoActive || selectionActive
        if (!active) return
        val x = if (lassoActive) lassoX else selX
        val y = if (lassoActive) lassoY else selY
        val r = if (lassoActive) lassoR else selR
        val c = Palette.of(PLAYER)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.setColor(c.r * 0.35f, c.g * 0.45f, c.b, if (lassoActive) 0.22f else 0.32f)
        draw(textures.disc, x, y, r * 2f)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        batch.setColor(c.r, c.g, c.b, 0.9f)
        draw(textures.ring, x, y, r * 2f / 0.92f)
    }

    private fun orbitRadius(p: Planet): Float =
        sim.config.planetRadius(p.level) + sim.config.orbitGap + sim.config.orbitJitter / 2

    /** Raio desenhado da esfera: neutros ficam menores (AJ-16). */
    private fun sphereRadius(p: Planet): Float =
        sim.config.planetRadius(p.level) * (if (p.level >= 1) 0.8f else 0.55f)

    /** Converte eventos do tick em efeitos. */
    private fun consumeEvents() {
        val e = sim.events
        for (k in 0 until e.count) {
            val c = Palette.of(e.faction[k])
            when (e.type[k]) {
                SimEvents.DEATH -> particles.death(e.x[k], e.y[k], c, e.amount[k])
                SimEvents.CAPTURE -> {
                    particles.ring(e.x[k], e.y[k], c, 40f, 260f, 0.8f)
                    particles.flash(e.x[k], e.y[k], c, 220f, 0.5f)
                }
                SimEvents.LEVEL_UP -> particles.ring(e.x[k], e.y[k], Color.WHITE, 60f, 200f, 0.6f)
                SimEvents.DESTROYED -> {
                    particles.flash(e.x[k], e.y[k], c, 300f, 0.7f)
                    particles.spark(e.x[k], e.y[k], c, 24)
                }
            }
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun drawOverlays() {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Line)
        for (p in sim.planets) {
            val r = sphereRadius(p)
            val c = Palette.of(p.owner)
            if (p.level >= 1) {
                val max = Rules.maxHealth(p.level)
                if (p.health < max) {
                    shapes.color = c
                    arc(p.x, p.y, r + 4f, p.health / max.toFloat())
                }
            }
            if (p.growth > 0) {
                shapes.color = if (p.level == 0) c else Color.WHITE
                arc(p.x, p.y, r + 10f, p.growth / Rules.growthCost(p.level).toFloat())
            }
        }
        if (orderFxTime > 0f) {
            val t = 1f - orderFxTime / ORDER_FX_SECONDS
            shapes.setColor(1f, 1f, 1f, 1f - t)
            shapes.circle(orderFxX, orderFxY, 10f + 40f * t, 32)
        }
        if (debugAi) drawAiDebug()
        shapes.end()
    }

    /** Overlay de debug da IA: linhas fonte → alvo das últimas decisões de cada bot (tecla D no desktop). */
    private fun drawAiDebug() {
        for (bot in bots.bots) {
            val c = Palette.of(bot.faction)
            for (d in bot.lastDecisions) {
                val t = sim.planets[d.targetPlanet]
                shapes.setColor(c.r, c.g, c.b, 0.55f)
                var k = 0
                while (k < d.sourcePoints.size) {
                    shapes.line(d.sourcePoints[k], d.sourcePoints[k + 1], t.x, t.y)
                    k += 2
                }
                shapes.circle(t.x, t.y, sim.config.planetRadius(t.level) + 26f, 32)
            }
        }
    }

    /** Arco só com o contorno, no sentido horário a partir do topo (o `ShapeRenderer.arc` desenha os raios até o centro). */
    private fun arc(x: Float, y: Float, radius: Float, fraction: Float) {
        if (fraction <= 0f) return
        val segments = maxOf(2, (ARC_SEGMENTS * fraction.coerceAtMost(1f)).toInt())
        val sweep = -MathUtils.PI2 * fraction.coerceAtMost(1f)
        var px = x
        var py = y + radius
        for (k in 1..segments) {
            val a = MathUtils.HALF_PI + sweep * k / segments
            val nx = x + MathUtils.cos(a) * radius
            val ny = y + MathUtils.sin(a) * radius
            shapes.line(px, py, nx, ny)
            px = nx; py = ny
        }
    }

    private fun drawHud() {
        hudViewport.apply(true)
        batch.projectionMatrix = hudViewport.camera.combined
        batch.begin()
        val top = hudViewport.worldHeight - 16f
        var x = 16f
        for (f in sim.factions) {
            font.color = if (f.eliminated) Color.DARK_GRAY else Palette.of(f.id)
            hudText.setLength(0)
            hudText.append(Palette.factionNames[f.id]).append(' ').append(sim.totalPower(f.id))
            x += font.draw(batch, hudText, x, top).width + 28f
        }
        font.color = Color.GRAY
        hudText.setLength(0)
        hudText.append(fps).append(" fps")
        font.draw(batch, hudText, hudViewport.worldWidth - 120f, top)
        if (debugAi) drawAiDebugText()

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

    private fun drawAiDebugText() {
        var y = hudViewport.worldHeight - 60f
        for (bot in bots.bots) {
            font.color = Palette.of(bot.faction)
            val summary = bot.lastDecisions.joinToString("  ") { "${it.kind.name.lowercase()}->P${it.targetPlanet} (${it.sent}/${it.need})" }
            font.draw(batch, "${bot.weights.name}: $summary", 16f, y)
            y -= 30f
        }
    }

    private fun draw(texture: TextureRegion, cx: Float, cy: Float, size: Float) {
        batch.draw(texture, cx - size / 2, cy - size / 2, size, size)
    }

    /** Anel achatado verticalmente (órbita vista em perspectiva). */
    private fun drawEllipse(texture: TextureRegion, cx: Float, cy: Float, radius: Float, squash: Float) {
        val w = radius * 2f / 0.92f
        val h = w * squash
        batch.draw(texture, cx - w / 2, cy - h / 2, w, h)
    }

    private fun Color.withAlpha(a: Float): Color = tint.set(r, g, b, a)

    private fun updateStats(delta: Float) {
        statFrames++
        statElapsed += delta
        if (statElapsed >= 1f) {
            fps = statFrames
            val alive = (0 until sim.factions.size).sumOf { sim.unitCount(it) }
            Gdx.app.log(LOG_TAG, "fps=$fps tick=${sim.tick} units=$alive simMsPerFrame=${statSimNanos / 1_000_000 / statFrames} heapKB=${Gdx.app.javaHeap / 1024}")
            statSimNanos = 0
            statFrames = 0
            statElapsed = 0f
        }
    }

    // ---- entrada ----

    override fun onLasso(centerX: Float, centerY: Float, radius: Float, finished: Boolean) {
        if (isGameOver()) return
        selectedPlanet = -1
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
            selectedPlanet = -1
        } else if (planet != null && planet.owner == PLAYER) {
            // Toque em qualquer parte do próprio planeta (inclusive no anel) seleciona todas as unidades dele.
            selectionActive = true
            selectedPlanet = planet.id
            selX = planet.x; selY = planet.y
            selR = orbitRadius(planet) + sim.config.orbitJitter + 14f
        }
    }

    private fun planetAt(x: Float, y: Float): Planet? {
        var best: Planet? = null
        var bestD = Float.MAX_VALUE
        for (p in sim.planets) {
            // Área de toque = anel de órbita inteiro + margem (o planeta desenhado é pequeno dentro do anel).
            val reach = orbitRadius(p) + sim.config.orbitJitter + TAP_MARGIN
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
        bots = BotDriver.standard(sim, seed)
        prevX = FloatArray(0); prevY = FloatArray(0); prevAlive = BooleanArray(0)
        selectionActive = false
        selectedPlanet = -1
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
        backdrop.dispose()
        font.dispose()
    }

    companion object {
        const val LOG_TAG = "ORBITA"
        const val PLAYER = 0
        private const val STEP = 1f / 60f
        private const val MAX_FRAME_DELTA = 0.25f
        private const val MAX_STEPS_PER_FRAME = 4
        private const val UNIT_SIZE = 15f
        private const val UNIT_CORE = 5.5f
        private const val COMET_LENGTH = 7f
        private const val TAP_MARGIN = 22f
        private const val MIN_SELECTION_RADIUS = 12f
        private const val ORDER_FX_SECONDS = 0.5f
        private const val ARC_SEGMENTS = 48

        /** Achatamento vertical da órbita em perspectiva (AJ-07). */
        private const val ORBIT_TILT = 0.38f
        private val ATOM_COS = MathUtils.cosDeg(38f)
        private val ATOM_SIN = MathUtils.sinDeg(38f)
    }
}
