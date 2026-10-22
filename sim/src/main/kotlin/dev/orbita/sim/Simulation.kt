package dev.orbita.sim

import dev.orbita.sim.UnitStore.Companion.MODE_HOLD
import dev.orbita.sim.UnitStore.Companion.MODE_MOVE_PLANET
import dev.orbita.sim.UnitStore.Companion.MODE_MOVE_POINT
import dev.orbita.sim.UnitStore.Companion.MODE_ORBIT
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Núcleo determinístico da partida (spec R1–R9). Avança em ticks fixos via [step].
 * Comandos enviados com [submit] são aplicados no início do próximo tick e registrados em [commandLog].
 */
class Simulation(
    val map: MapDef,
    val seed: Long,
    val config: SimConfig = SimConfig(),
) {
    val planets: List<Planet> = map.planets.mapIndexed { i, d -> Planet(i, d.x, d.y, d.maxLevel) }
    val factions: List<Faction> = List(map.factionCount) { Faction(it) }
    val units = UnitStore()

    var tick = 0
        private set

    /** Facção vencedora ou [NO_FACTION] enquanto a partida não terminou (R7). */
    var winner = NO_FACTION
        private set

    val commandLog: List<TimedCommand> get() = log

    private val rng = Rng(seed)
    private val pending = ArrayList<Command>()
    private val log = ArrayList<TimedCommand>()
    private val unitCount = IntArray(map.factionCount)
    private val powerSum = IntArray(map.factionCount)

    // Grade espacial por facção (D14); célula = alcance de engajamento.
    private val cellSize = config.engageRange
    private val cols = (map.width / cellSize).toInt() + 1
    private val rows = (map.height / cellSize).toInt() + 1
    private val cellCount = cols * rows
    private val cellHead = IntArray(map.factionCount * cellCount)
    private var cellNext = IntArray(units.capacity)

    init {
        map.planets.forEachIndexed { i, d ->
            val p = planets[i]
            p.owner = d.owner
            p.level = d.level
            p.health = d.health ?: if (d.level >= 1) Rules.maxHealth(d.level) else 0
            p.growth = d.growth
        }
        map.planets.forEachIndexed { i, d ->
            if (d.startUnits > 0 && d.owner != NO_FACTION) spawnRing(planets[i], d.owner, d.startUnits)
        }
    }

    fun unitCount(faction: Int): Int = unitCount[faction]

    fun totalPower(faction: Int): Int = powerSum[faction]

    fun submit(command: Command) {
        pending.add(command)
    }

    /** Tempo acumulado por fase do [step] (ns), quando [profile] está ligado: comandos+produção, grade, busca, movimento, choques, eliminação. */
    val phaseNanos = LongArray(6)
    var profile = false

    fun step() {
        if (!profile) {
            applyPendingCommands()
            if (config.productionEnabled && tick % config.spawnPeriodTicks == 0) produce()
            rebuildGrid()
            scanForEnemies()
            moveUnits()
            resolveClashes()
            updateElimination()
        } else {
            var t = System.nanoTime()
            fun lap(phase: Int) { val now = System.nanoTime(); phaseNanos[phase] += now - t; t = now }
            applyPendingCommands()
            if (config.productionEnabled && tick % config.spawnPeriodTicks == 0) produce()
            lap(0)
            rebuildGrid(); lap(1)
            scanForEnemies(); lap(2)
            moveUnits(); lap(3)
            resolveClashes(); lap(4)
            updateElimination(); lap(5)
        }
        tick++
    }

    /** Hash do estado completo, para testes de determinismo (R9). */
    fun stateHash(): Long {
        var h = -3750763034362895579L // FNV offset
        fun mix(v: Long) {
            h = (h xor v) * 1099511628211L
        }
        mix(tick.toLong())
        for (p in planets) {
            mix(p.owner.toLong()); mix(p.level.toLong()); mix(p.health.toLong()); mix(p.growth.toLong())
        }
        for (i in 0 until units.highWater) {
            if (!units.alive[i]) { mix(-1); continue }
            mix(units.owner[i].toLong()); mix(units.power[i].toLong()); mix(units.mode[i].toLong())
            mix(units.lock[i].toLong())
            mix(units.x[i].toRawBits().toLong()); mix(units.y[i].toRawBits().toLong())
        }
        return h
    }

    // ---- comandos (R8) ----

    private fun applyPendingCommands() {
        for (c in pending) {
            log.add(TimedCommand(tick, c))
            when (c) {
                is SendUnits -> applySend(c)
            }
        }
        pending.clear()
    }

    private fun applySend(c: SendUnits) {
        if (c.faction !in factions.indices || factions[c.faction].eliminated) return
        if (c.target is Target.PlanetRef && c.target.planetId !in planets.indices) return
        val r2 = c.radius * c.radius
        var sent = 0
        for (i in 0 until units.highWater) {
            if (!units.alive[i] || units.owner[i] != c.faction) continue
            val dx = units.x[i] - c.fromX
            val dy = units.y[i] - c.fromY
            if (dx * dx + dy * dy > r2) continue
            if (sent + units.power[i] > c.maxPower) continue
            sent += units.power[i]
            when (val t = c.target) {
                is Target.Point -> {
                    units.mode[i] = MODE_MOVE_POINT
                    units.targetX[i] = t.x
                    units.targetY[i] = t.y
                }
                is Target.PlanetRef -> {
                    units.mode[i] = MODE_MOVE_PLANET
                    units.targetPlanet[i] = t.planetId
                }
            }
        }
    }

    // ---- produção (R3) ----

    private fun produce() {
        for (p in planets) {
            if (p.level < 1 || p.owner == NO_FACTION) continue
            val faction = factions[p.owner]
            if (faction.eliminated) continue
            val count = Rules.unitsPerSpawn(p.level)
            val start = rng.nextFloat() * TWO_PI
            for (k in 0 until count) {
                if (faction.dropUnit()) continue
                spawnAt(p, p.owner, start + k * TWO_PI / count)
            }
        }
    }

    private fun spawnRing(p: Planet, owner: Int, count: Int) {
        val start = rng.nextFloat() * TWO_PI
        for (k in 0 until count) spawnAt(p, owner, start + k * TWO_PI / count)
    }

    private fun spawnAt(p: Planet, owner: Int, angle: Float) {
        val r = config.planetRadius(p.level) + config.orbitGap
        val i = addUnit(owner, 1, p.x + r * cos(angle), p.y + r * sin(angle))
        enterOrbit(i, p)
    }

    /** Cria uma unidade parada (mode HOLD). Usado pela produção e por testes. */
    internal fun addUnit(owner: Int, power: Int, x: Float, y: Float): Int {
        val i = units.allocate()
        if (units.capacity > cellNext.size) cellNext = cellNext.copyOf(units.capacity)
        units.owner[i] = owner
        units.power[i] = power
        units.x[i] = x
        units.y[i] = y
        unitCount[owner]++
        powerSum[owner] += power
        return i
    }

    private fun setPower(i: Int, power: Int) {
        powerSum[units.owner[i]] += power - units.power[i]
        units.power[i] = power
    }

    private fun kill(i: Int) {
        val partner = units.lock[i]
        if (partner >= 0 && units.lock[partner] == i) unlock(partner)
        unitCount[units.owner[i]]--
        powerSum[units.owner[i]] -= units.power[i]
        units.release(i)
    }

    private fun enterOrbit(i: Int, p: Planet) {
        var dx = units.x[i] - p.x
        var dy = units.y[i] - p.y
        var d = sqrt(dx * dx + dy * dy)
        if (d < 1e-3f) { dx = 1f; dy = 0f; d = 1f }
        val r = config.planetRadius(p.level) + config.orbitGap + rng.nextFloat() * config.orbitJitter
        units.orbitX[i] = dx / d * r
        units.orbitY[i] = dy / d * r
        val w = config.orbitSpeed / config.tickHz / r
        units.orbitCos[i] = StrictMath.cos(w.toDouble()).toFloat()
        units.orbitSin[i] = StrictMath.sin(w.toDouble()).toFloat()
        units.home[i] = p.id
        units.mode[i] = MODE_ORBIT
    }

    // ---- combate (R5) ----

    /** Só unidades livres entram na grade: travadas não podem ser alvo de nova trava. */
    private fun rebuildGrid() {
        cellHead.fill(-1)
        for (i in 0 until units.highWater) {
            if (!units.alive[i] || units.lock[i] >= 0) continue
            val slot = units.owner[i] * cellCount + cellIndex(units.x[i], units.y[i])
            cellNext[i] = cellHead[slot]
            cellHead[slot] = i
        }
    }

    private fun cellIndex(x: Float, y: Float): Int {
        val cx = (x / cellSize).toInt().coerceIn(0, cols - 1)
        val cy = (y / cellSize).toInt().coerceIn(0, rows - 1)
        return cy * cols + cx
    }

    private fun scanForEnemies() {
        val mask = config.scanPeriodTicks - 1
        val phase = tick and mask
        val range2 = config.engageRange * config.engageRange
        for (i in 0 until units.highWater) {
            if (!units.alive[i] || units.lock[i] >= 0 || (i and mask) != phase) continue
            val cell = cellIndex(units.x[i], units.y[i])
            val cx = cell % cols
            val cy = cell / cols
            val f = units.owner[i]
            var best = -1
            var bestD = range2
            for (of in factions.indices) {
                if (of == f) continue
                val base = of * cellCount
                for (ny in maxOf(0, cy - 1)..minOf(rows - 1, cy + 1)) {
                    for (nx in maxOf(0, cx - 1)..minOf(cols - 1, cx + 1)) {
                        var j = cellHead[base + ny * cols + nx]
                        while (j >= 0) {
                            if (units.alive[j] && units.lock[j] < 0) {
                                val dx = units.x[j] - units.x[i]
                                val dy = units.y[j] - units.y[i]
                                val d = dx * dx + dy * dy
                                if (d < bestD) { bestD = d; best = j }
                            }
                            j = cellNext[j]
                        }
                    }
                }
            }
            if (best >= 0) {
                units.lock[i] = best
                units.lock[best] = i
            }
        }
    }

    private fun resolveClashes() {
        for (i in 0 until units.highWater) {
            if (!units.alive[i]) continue
            val j = units.lock[i]
            if (j <= i || !units.alive[j]) continue
            val dx = units.x[j] - units.x[i]
            val dy = units.y[j] - units.y[i]
            val reach = config.clashDistance(maxOf(units.power[i], units.power[j]))
            if (dx * dx + dy * dy > reach * reach) continue
            // ref: MECH-004 — m = min(a, b); ambos perdem m
            val m = min(units.power[i], units.power[j])
            units.lock[i] = -1
            units.lock[j] = -1
            setPower(i, units.power[i] - m)
            setPower(j, units.power[j] - m)
            if (units.power[i] == 0) kill(i) else resumeAfterCombat(i)
            if (units.power[j] == 0) kill(j) else resumeAfterCombat(j)
        }
    }

    private fun unlock(i: Int) {
        units.lock[i] = -1
        resumeAfterCombat(i)
    }

    private fun resumeAfterCombat(i: Int) {
        if (units.mode[i] == MODE_ORBIT) enterOrbit(i, planets[units.home[i]])
    }

    // ---- movimento e chegada (R4, R6) ----

    private fun moveUnits() {
        val speed = config.speedPerTick
        for (i in 0 until units.highWater) {
            if (!units.alive[i]) continue
            val partner = units.lock[i]
            if (partner >= 0) {
                val dx = units.x[partner] - units.x[i]
                val dy = units.y[partner] - units.y[i]
                val d = sqrt(dx * dx + dy * dy)
                if (d > 1e-3f) {
                    val stepLen = min(speed, d * 0.5f)
                    units.x[i] += dx / d * stepLen
                    units.y[i] += dy / d * stepLen
                }
                continue
            }
            when (units.mode[i]) {
                MODE_ORBIT -> {
                    val p = planets[units.home[i]]
                    val ox = units.orbitX[i]
                    val oy = units.orbitY[i]
                    val c = units.orbitCos[i]
                    val s = units.orbitSin[i]
                    units.orbitX[i] = ox * c - oy * s
                    units.orbitY[i] = ox * s + oy * c
                    units.x[i] = p.x + units.orbitX[i]
                    units.y[i] = p.y + units.orbitY[i]
                }
                MODE_MOVE_POINT -> {
                    if (stepToward(i, units.targetX[i], units.targetY[i], speed, 0f)) units.mode[i] = MODE_HOLD
                }
                MODE_MOVE_PLANET -> {
                    val p = planets[units.targetPlanet[i]]
                    val reach = config.planetRadius(p.level) + config.arriveMargin
                    if (stepToward(i, p.x, p.y, speed, reach)) arrive(i, p)
                }
            }
        }
    }

    /** Move a unidade até [stopAt] do alvo; retorna true quando chegou. */
    private fun stepToward(i: Int, tx: Float, ty: Float, speed: Float, stopAt: Float): Boolean {
        val dx = tx - units.x[i]
        val dy = ty - units.y[i]
        val d = sqrt(dx * dx + dy * dy)
        if (d <= stopAt) return true
        val remaining = d - stopAt
        if (remaining <= speed) {
            units.x[i] += dx / d * remaining
            units.y[i] += dy / d * remaining
            return true
        }
        units.x[i] += dx / d * speed
        units.y[i] += dy / d * speed
        return false
    }

    private fun arrive(i: Int, p: Planet) {
        val result = absorb(p, units.owner[i], units.power[i])
        when {
            result == REFUSED -> enterOrbit(i, p)
            result == 0 -> kill(i)
            // Excedente: segue com a ordem e é absorvido de novo no próximo tick
            // (ou fica parado se o planeta foi destruído e as ordens canceladas).
            else -> setPower(i, result)
        }
    }

    /**
     * Aplica a chegada de uma unidade de [power] da facção [faction] em [p] (spec R6).
     * Retorna o power restante na unidade (0 = consumida) ou [REFUSED].
     */
    private fun absorb(p: Planet, faction: Int, power: Int): Int {
        var remaining: Int
        if (p.level >= 1) {
            if (p.owner == faction) {
                val maxH = Rules.maxHealth(p.level)
                if (p.health < maxH) {
                    val heal = min(power, maxH - p.health)
                    p.health += heal
                    remaining = power - heal
                } else if (p.level < p.maxLevel) {
                    val add = min(power, Rules.growthCost(p.level) - p.growth)
                    p.growth += add
                    remaining = power - add
                } else {
                    return REFUSED
                }
            } else {
                val damage = min(power, p.health)
                p.health -= damage
                remaining = power - damage
                if (p.health == 0) {
                    destroy(p)
                    return remaining
                }
            }
        } else if (p.owner == faction) {
            val add = min(power, Rules.growthCost(0) - p.growth)
            p.growth += add
            remaining = power - add
        } else {
            p.growth -= power
            remaining = 0
            if (p.growth < 0) {
                p.owner = faction
                p.growth = -p.growth
                val cost = Rules.growthCost(0)
                if (p.growth > cost) {
                    remaining = p.growth - cost
                    p.growth = cost
                }
            }
        }
        levelUpIfReady(p)
        return remaining
    }

    private fun levelUpIfReady(p: Planet) {
        val cost = Rules.growthCost(p.level)
        if (p.growth < cost || p.level >= p.maxLevel) return
        p.level += 1
        p.health = Rules.maxHealth(p.level)
        p.growth = if (p.level == p.maxLevel) 0 else p.growth - cost
    }

    /** Planeta destruído: volta ao nível 0 e mantém o dono; ordens contra ele são canceladas. ref: MECH-002, LAC-006 */
    private fun destroy(p: Planet) {
        p.level = 0
        p.growth = 0
        p.health = 0
        for (i in 0 until units.highWater) {
            if (units.alive[i] && units.mode[i] == MODE_MOVE_PLANET && units.targetPlanet[i] == p.id) {
                units.mode[i] = MODE_HOLD
            }
        }
    }

    // ---- fim de partida (R7) ----

    private fun updateElimination() {
        var aliveCount = 0
        var last = NO_FACTION
        for (f in factions) {
            if (!f.eliminated) {
                val hasPlanet = planets.any { it.owner == f.id && it.level >= 1 }
                if (!hasPlanet && unitCount[f.id] == 0) f.eliminated = true
            }
            if (!f.eliminated) { aliveCount++; last = f.id }
        }
        if (winner == NO_FACTION && aliveCount == 1 && factions.size > 1) winner = last
    }

    companion object {
        const val REFUSED = -1
        private const val TWO_PI = (2.0 * Math.PI).toFloat()

        private fun cos(a: Float): Float = StrictMath.cos(a.toDouble()).toFloat()
        private fun sin(a: Float): Float = StrictMath.sin(a.toDouble()).toFloat()

        /** Reconstrói uma partida a partir do log de comandos (R9). */
        fun replay(map: MapDef, seed: Long, config: SimConfig, log: List<TimedCommand>, untilTick: Int): Simulation {
            val sim = Simulation(map, seed, config)
            var next = 0
            while (sim.tick < untilTick) {
                while (next < log.size && log[next].tick == sim.tick) sim.submit(log[next++].command)
                sim.step()
            }
            return sim
        }
    }
}
