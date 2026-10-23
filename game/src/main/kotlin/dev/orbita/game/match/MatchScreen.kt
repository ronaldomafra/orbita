package dev.orbita.game.match

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import dev.orbita.ai.BotDriver
import dev.orbita.game.settings.Params
import dev.orbita.game.settings.Settings
import dev.orbita.sim.NO_FACTION
import dev.orbita.sim.Planet
import dev.orbita.sim.Rules
import dev.orbita.sim.SendUnits
import dev.orbita.sim.SimEvents
import dev.orbita.sim.Simulation
import dev.orbita.sim.MapLibrary
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
    private val settings: Settings,
    /** Abre o menu de ajustes (a partida fica pausada enquanto ele está aberto). */
    private val onOpenSettings: () -> Unit,
    /** Abre a lista de mapas. */
    private val onOpenMaps: () -> Unit,
    private val seed: Long = System.nanoTime(),
    private var debugAi: Boolean = false,
    /** > 0 substitui as unidades iniciais de cada base (teste de estresse). */
    private val startUnitsOverride: Int = 0,
    private val theme: NebulaTheme = NebulaTheme.DEEP_VIOLET,
    /** Zoom inicial da câmera (depuração: abrir já aproximado). */
    initialZoom: Float = 1f,
) : KtxScreen, MatchInput.Listener {

    /** Overlay da IA forçado pelo launcher (`--ez debugAi true`), além do ajuste. */
    private val debugAiForced = debugAi

    private var sim = newSimulation()
    private var bots = newBots()

    // Parâmetros visuais lidos do menu de ajustes (ver applyLiveSettings).
    private var unitSize = 15f
    private var unitCore = 5.5f
    private var cometLength = 7f
    private var orbitTail = 4f
    private var orbitTilt = 0.38f
    private var auraIntensity = 1f
    private var pulseScale = 0.1f
    private var showFps = true
    private var showProfiles = true

    private val rig = CameraRig(sim.map.width, sim.map.height)
    private val hudViewport = ScreenViewport()
    private val batch = SpriteBatch(8191)
    private val shapes = ShapeRenderer()
    private val textures = Textures()
    private val planetTextures = PlanetTextures()
    private var backdropTheme = backdropThemeFromSettings()
    private var backdrop = Backdrop(backdropTheme, textures, sim.map.width, sim.map.height)
    private val font = BitmapFont().apply { data.setScale(1.6f) }
    private val tint = Color()
    private val particles = Particles(textures)
    private val input = MatchInput(rig, sim.map.width, sim.map.height, this)
    private var time = 0f

    // Posições do tick anterior, para interpolação e para a direção dos cometas.
    private var prevX = FloatArray(0)
    private var prevY = FloatArray(0)
    private var prevAlive = BooleanArray(0)
    private var accumulator = 0f

    // Posição de desenho de cada unidade (após interpolação e projeção da órbita) e velocidade projetada.
    private var drawX = FloatArray(0)
    private var drawY = FloatArray(0)
    private var drawVX = FloatArray(0)
    private var drawVY = FloatArray(0)

    // Pulso de cada planeta a cada leva de produção (AJ-21), de 1 a 0.
    private var pulse = FloatArray(0)

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
    private var statSimNanos = 0L
    private val hudText = StringBuilder(32)
    private var statElapsed = 0f
    private var fps = 0

    init {
        applyLiveSettings()
        rig.zoom = initialZoom
        // Aproximado: centraliza na base do jogador.
        if (initialZoom < 1f) sim.planets.firstOrNull { it.owner == PLAYER }?.let { rig.targetX = it.x; rig.targetY = it.y }
    }

    private fun newSimulation(): Simulation {
        val base = MapLibrary.byId(settings.mapId).build()
        val startUnits = if (startUnitsOverride > 0) startUnitsOverride else settings.int(Params.START_UNITS)
        val map = base.copy(planets = base.planets.map { if (it.owner >= 0) it.copy(startUnits = startUnits) else it })
        return Simulation(map, seed, settings.simConfig())
    }

    private fun newBots(): BotDriver = BotDriver.standard(
        sim, seed,
        dropRate = settings.int(Params.BOT_HANDICAP),
        personalities = settings.personalities(),
        startTick = settings.int(Params.AI_START),
    )

    private fun backdropThemeFromSettings(): NebulaTheme =
        theme.copy(starCount = settings.int(Params.STARS), nebulaOpacity = settings[Params.NEBULA])

    /** Lê os parâmetros de Visual e Câmera (valem ao voltar do menu). */
    private fun applyLiveSettings() {
        unitSize = settings[Params.UNIT_SIZE]
        unitCore = settings[Params.UNIT_CORE]
        cometLength = settings[Params.COMET_LENGTH]
        orbitTail = settings[Params.ORBIT_TAIL]
        orbitTilt = settings[Params.ORBIT_TILT]
        auraIntensity = settings[Params.AURA]
        pulseScale = settings[Params.PULSE]
        showFps = settings.bool(Params.SHOW_FPS)
        showProfiles = settings.bool(Params.SHOW_PROFILES)
        debugAi = settings.bool(Params.DEBUG_AI) || debugAiForced
        rig.configure(settings[Params.FOV], settings[Params.MAX_PITCH], settings[Params.MIN_ZOOM], settings[Params.MAX_ZOOM])
        input.edgeResistance = settings[Params.EDGE_RESISTANCE]
        input.returnRate = settings[Params.RETURN_RATE]
        val newTheme = backdropThemeFromSettings()
        if (newTheme != backdropTheme) {
            backdrop.dispose()
            backdropTheme = newTheme
            backdrop = Backdrop(newTheme, textures, sim.map.width, sim.map.height)
        }
    }

    /** Botão "Ajustes" no HUD e tecla Voltar/Esc; o resto vai para os gestos da partida. */
    private val hudInput = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val y = Gdx.graphics.height - screenY
            if (y < settingsButtonY) return false
            if (screenX >= settingsButtonX) { onOpenSettings(); return true }
            if (screenX >= mapsButtonX) { onOpenMaps(); return true }
            return false
        }

        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { onOpenSettings(); return true }
            return false
        }
    }
    private var settingsButtonX = 0f
    private var mapsButtonX = 0f
    private var settingsButtonY = 0f

    override fun show() {
        applyLiveSettings()
        Gdx.input.inputProcessor = InputMultiplexer(hudInput, input)
    }

    /** Recomeça a partida com os parâmetros atuais de Partida, Simulação e IA. */
    fun restartWithSettings() {
        restart()
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
        val decay = kotlin.math.exp(-delta * PULSE_DECAY)
        for (k in pulse.indices) pulse[k] *= decay
        if (orderFxTime > 0f) orderFxTime -= delta

        clearScreen(0f, 0f, 0f)
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        rig.update()
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
        batch.projectionMatrix = rig.camera.combined
        batch.begin()
        backdrop.draw(batch, time)
        computeDrawPositions(alpha)

        // No plano (perspectiva): trilhas de órbita, uma por plano (AJ-19), e o disco de seleção.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        if (pulse.size != sim.planets.size) pulse = FloatArray(sim.planets.size)
        for (p in sim.planets) {
            if (p.level < 1) continue
            val orbit = orbitRadius(p)
            val planes = planeCount(p)
            batch.setColor(0.55f, 0.6f, 0.7f, 0.08f)
            for (k in 0 until planes) drawEllipse(textures.ring, p.x, p.y, orbit, orbitSquash(), planeAngle(planes, k))
        }
        drawSelectionDisc()

        // Em pé (espaço de tela): sprites redondos com a escala da profundidade (REF-06).
        batch.projectionMatrix = rig.screenMatrix
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (p in sim.planets) {
            if (p.level < 1) continue
            val r = sim.config.planetRadius(p.level)
            val c = Palette.of(p.owner)
            val breathe = 1f + 0.04f * MathUtils.sin(time * 1.4f + p.id * 1.7f)
            val beat = pulse[p.id]
            // Aura grande e tênue (AJ-20) + coroa; ambas reforçadas no pulso de produção (AJ-21).
            batch.setColor(c.withAlpha((0.11f + 0.08f * beat) * auraIntensity))
            drawStanding(textures.softGlow, p.x, p.y, r * 9f * breathe)
            batch.setColor(c.withAlpha((0.28f + 0.3f * beat) * auraIntensity))
            drawStanding(textures.softGlow, p.x, p.y, r * (4.2f + 0.8f * beat) * breathe)
        }

        // Unidades atrás do planeta (metade de cima do anel inclinado).
        drawUnits(behind = true)

        // Esferas (normal) e borda acesa (aditivo).
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        for (p in sim.planets) {
            val r = sphereRadius(p) * (1f + pulseScale * pulse[p.id])
            val c = Palette.of(p.owner)
            when {
                p.level >= 1 -> batch.setColor(lerp(c.r, 1f, 0.1f), lerp(c.g, 1f, 0.1f), lerp(c.b, 1f, 0.1f), 1f)
                p.owner != NO_FACTION -> batch.setColor(c.r * 0.4f + 0.2f, c.g * 0.4f + 0.2f, c.b * 0.4f + 0.2f, 1f)
                else -> batch.setColor(0.78f, 0.80f, 0.84f, 1f) // neutro: cinza claro, sem brilho (AJ-16)
            }
            // Superfície texturizada (AJ-17): variante fixa por planeta; sempre redonda na tela.
            drawStanding(planetTextures.variants[p.id % PlanetTextures.VARIANTS], p.x, p.y, r * 2f)
        }
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        for (p in sim.planets) {
            if (p.level < 1) continue
            val r = sphereRadius(p)
            val c = Palette.of(p.owner)
            batch.setColor(c.withAlpha(0.55f))
            drawStanding(textures.ring, p.x, p.y, r * 2.1f)
            // Pontos de nível abaixo do anel: acesos até o nível atual.
            for (lv in 1..p.maxLevel) {
                batch.setColor(c.withAlpha(if (lv <= p.level) 1f else 0.25f))
                drawStanding(textures.spark, p.x + (lv - (p.maxLevel + 1) / 2f) * 12f, p.y - orbitRadius(p) * orbitSquash() - 14f, 12f)
            }
        }

        // Unidades na frente do planeta, paradas e em trânsito (cometas).
        drawUnits(behind = false)

        particles.draw(batch, rig)
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
            drawVX = drawVX.copyOf(u.capacity)
            drawVY = drawVY.copyOf(u.capacity)
        }
        for (i in 0 until u.highWater) {
            if (!u.alive[i]) continue
            val interpolate = i < prevAlive.size && prevAlive[i]
            var x = if (interpolate) prevX[i] + (u.x[i] - prevX[i]) * alpha else u.x[i]
            var y = if (interpolate) prevY[i] + (u.y[i] - prevY[i]) * alpha else u.y[i]
            var vx = if (interpolate) u.x[i] - prevX[i] else 0f
            var vy = if (interpolate) u.y[i] - prevY[i] else 0f
            if (u.mode[i] == UnitStore.MODE_ORBIT && u.lock[i] < 0) {
                // Órbita em perspectiva: achata o eixo vertical e gira para o plano da unidade (AJ-07/AJ-19).
                val p = sim.planets[u.home[i]]
                val planes = planeCount(p)
                val angle = planeAngle(planes, i % planes)
                val c = MathUtils.cosDeg(angle)
                val s = MathUtils.sinDeg(angle)
                val ox = x - p.x
                val oy = (y - p.y) * orbitSquash()
                x = p.x + ox * c - oy * s
                y = p.y + ox * s + oy * c
                val pvy = vy * orbitSquash()
                val pvx = vx
                vx = pvx * c - pvy * s
                vy = pvx * s + pvy * c
            }
            drawX[i] = x
            drawY[i] = y
            drawVX[i] = vx
            drawVY[i] = vy
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

            if (!rig.project(drawX[i], drawY[i])) continue
            val x = rig.screenX
            val y = rig.screenY
            val ppu = rig.pixelsPerUnit
            val scale = sqrt(u.power[i].toFloat()) * ppu
            var selected = false
            if (selectionActive && u.owner[i] == PLAYER) {
                val dx = u.x[i] - selX
                val dy = u.y[i] - selY
                selected = dx * dx + dy * dy <= sel2
            }
            val c = Palette.of(u.owner[i])
            val dim = if (isBehind) 0.6f else 1f

            // Rastro curto na direção do movimento: cometa em trânsito (AJ-12) e também em órbita (AJ-18).
            // Velocidade aproximada na tela: o eixo do plano encolhe com a inclinação.
            val vx = drawVX[i] * ppu
            val vy = drawVY[i] * ppu / rig.upright
            val speed = sqrt(vx * vx + vy * vy)
            if (speed > 0.3f * ppu) {
                val len = unitSize * scale + speed * (if (orbiting) orbitTail else cometLength)
                val width = unitSize * scale * 0.5f
                val angle = MathUtils.atan2(vy, vx) * MathUtils.radiansToDegrees
                batch.setColor(c.r, c.g, c.b, 0.6f * dim)
                // A cabeça fica na posição da unidade; o rastro se estende para trás.
                batch.draw(textures.glow, x - len + width / 2, y - width / 2, len - width / 2, width / 2, len, width, 1f, 1f, angle)
            }
            // "Conta de vidro" (AJ-18): halo da facção, anel fino em elipse e estrela de 4 pontas.
            if (selected) {
                batch.setColor(1f, 1f, 1f, 0.95f * dim) // unidades selecionadas ficam brancas (AJ-06)
                draw(textures.glow, x, y, unitSize * scale * 1.15f)
            } else {
                batch.setColor(c.r, c.g, c.b, 0.8f * dim)
                draw(textures.glow, x, y, unitSize * scale)
            }
            val ringW = unitSize * scale * 1.05f
            val ringH = ringW * 0.44f
            batch.setColor(lerp(c.r, 1f, 0.6f), lerp(c.g, 1f, 0.6f), lerp(c.b, 1f, 0.6f), 0.3f * dim)
            batch.draw(textures.ring, x - ringW / 2, y - ringH / 2, ringW, ringH)
            batch.setColor(1f, 1f, 1f, 0.9f * dim)
            draw(textures.star4, x, y, unitCore * scale * 1.5f)
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

    /** Um plano de órbita por nível (AJ-19). */
    private fun planeCount(p: Planet): Int = p.level.coerceIn(1, PLANE_ANGLES.size)

    private fun planeAngle(planes: Int, index: Int): Float = PLANE_ANGLES[planes - 1][index]

    private fun planetAtExact(x: Float, y: Float): Planet? = sim.planets.firstOrNull { it.x == x && it.y == y }

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
                SimEvents.SPAWN -> planetAtExact(e.x[k], e.y[k])?.let { if (it.id < pulse.size) pulse[it.id] = 1f }
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
        shapes.projectionMatrix = rig.camera.combined
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
            val color = if (f.eliminated) Color.DARK_GRAY else Palette.of(f.id)
            font.color = color
            hudText.setLength(0)
            hudText.append(Palette.factionNames[f.id]).append(' ').append(sim.totalPower(f.id))
            val width = font.draw(batch, hudText, x, top).width
            // Perfil do bot logo abaixo do nome (ajuste "Mostrar perfil dos bots").
            val profile = if (showProfiles) profileOf(f.id) else null
            if (profile != null) {
                val scaleX = font.data.scaleX
                val scaleY = font.data.scaleY
                font.data.setScale(scaleX * PROFILE_SCALE, scaleY * PROFILE_SCALE)
                font.setColor(color.r, color.g, color.b, 0.65f)
                font.draw(batch, profile, x, top - font.lineHeight / PROFILE_SCALE * 0.95f)
                font.data.setScale(scaleX, scaleY)
            }
            x += width + 28f
        }
        // Botões "Mapas" e "Ajustes" no canto superior direito (áreas de toque generosas).
        font.color = Color(0.75f, 0.85f, 0.95f, 1f)
        val label = font.draw(batch, "Ajustes", hudViewport.worldWidth - 140f, top)
        font.draw(batch, "Mapas", hudViewport.worldWidth - 280f, top)
        settingsButtonX = hudViewport.worldWidth - 165f
        mapsButtonX = hudViewport.worldWidth - 305f
        settingsButtonY = top - label.height - 30f
        if (showFps) {
            font.color = Color.GRAY
            hudText.setLength(0)
            hudText.append(fps).append(" fps")
            font.draw(batch, hudText, hudViewport.worldWidth - 410f, top)
        }
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

    /** Nome da personalidade do bot da facção, ou null para o jogador. */
    private fun profileOf(faction: Int): String? = bots.bots.firstOrNull { it.faction == faction }?.weights?.name

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

    /**
     * Sprite "em pé" em espaço de tela: projeta o ponto do plano e desenha redondo com a escala da
     * profundidade (exige `batch.projectionMatrix = rig.screenMatrix`).
     */
    private fun drawStanding(texture: TextureRegion, worldX: Float, worldY: Float, worldSize: Float) {
        if (!rig.project(worldX, worldY)) return
        draw(texture, rig.screenX, rig.screenY, worldSize * rig.pixelsPerUnit)
    }

    /**
     * Achatamento da órbita no plano: com a câmera inclinada o próprio plano já achata, então compensa
     * para a elipse na tela manter a mesma proporção (AJ-07 + REF-06).
     */
    private fun orbitSquash(): Float = (orbitTilt * rig.upright).coerceAtMost(1f)

    /** Anel achatado verticalmente (órbita vista em perspectiva), girado em [rotation] graus. */
    private fun drawEllipse(texture: TextureRegion, cx: Float, cy: Float, radius: Float, squash: Float, rotation: Float = 0f) {
        val w = radius * 2f / 0.92f
        val h = w * squash
        batch.draw(texture, cx - w / 2, cy - h / 2, w / 2, h / 2, w, h, 1f, 1f, rotation)
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
        } else if (planet != null && planet.owner == PLAYER) {
            // Toque em qualquer parte do próprio planeta (inclusive no anel) seleciona todas as unidades dele.
            selectionActive = true
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
        bots = newBots()
        prevX = FloatArray(0); prevY = FloatArray(0); prevAlive = BooleanArray(0)
        selectionActive = false
        lassoActive = false
        accumulator = 0f
    }

    override fun resize(width: Int, height: Int) {
        rig.resize(width, height)
        hudViewport.update(width, height, true)
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        textures.dispose()
        planetTextures.dispose()
        backdrop.dispose()
        font.dispose()
    }

    companion object {
        const val LOG_TAG = "ORBITA"
        const val PLAYER = 0
        private const val STEP = 1f / 60f
        private const val MAX_FRAME_DELTA = 0.25f
        private const val MAX_STEPS_PER_FRAME = 4
        private const val TAP_MARGIN = 22f
        private const val MIN_SELECTION_RADIUS = 12f
        private const val ORDER_FX_SECONDS = 0.5f
        private const val ARC_SEGMENTS = 48

        /** Inclinação de cada plano de órbita, por quantidade de planos (= nível). */
        private val PLANE_ANGLES = arrayOf(
            floatArrayOf(0f),
            floatArrayOf(-32f, 32f),
            floatArrayOf(0f, -58f, 58f),
            floatArrayOf(-22f, 22f, -66f, 66f),
            floatArrayOf(0f, -36f, 36f, -72f, 72f),
        )
        private const val PULSE_DECAY = 7f
        private const val PROFILE_SCALE = 0.72f
    }
}
