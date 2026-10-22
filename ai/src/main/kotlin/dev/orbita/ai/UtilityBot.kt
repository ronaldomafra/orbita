package dev.orbita.ai

import dev.orbita.sim.Command
import dev.orbita.sim.NO_FACTION
import dev.orbita.sim.Rng
import dev.orbita.sim.Rules
import dev.orbita.sim.SendUnits
import dev.orbita.sim.Target
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * IA por pontuação (spec §5): gera candidatos por planeta, estima a força necessária,
 * escolhe fontes por distância e emite até `maxOrders` envios por decisão.
 */
class UtilityBot(
    override val faction: Int,
    override val weights: StrategyWeights,
    seed: Long,
    /** Escalonamento entre bots para não decidirem no mesmo tick. */
    private val phaseOffset: Int = faction * 17,
) : BotBrain {

    private val rng = Rng(seed xor (faction.toLong() * 0x2545F4914F6CDD1DL))
    private val decisions = ArrayList<Decision>()
    override val lastDecisions: List<Decision> get() = decisions

    private class Source(val x: Float, val y: Float, val radius: Float, var available: Int, val planet: Int)

    private class Candidate(val kind: DecisionKind, val planet: Int, val need: Int, val value: Float) {
        var score = 0f
    }

    override fun isDue(tick: Int): Boolean =
        tick >= AI_START_TICK && (tick + phaseOffset) % weights.thinkPeriodTicks == 0

    override fun decide(obs: Observation): List<Command> {
        obs.refresh()
        decisions.clear()
        val sim = obs.sim
        if (sim.factions[faction].eliminated) return emptyList()

        val sources = collectSources(obs)
        if (sources.isEmpty()) return emptyList()

        val candidates = collectCandidates(obs)
        for (c in candidates) {
            val p = sim.planets[c.planet]
            val dist = nearestSourceDistance(sources, p.x, p.y, c.planet)
            val efficiency = 100f / (c.need + 50f)
            val distanceFactor = 1f + weights.distanceCost * dist / 1000f
            c.score = c.value * efficiency / distanceFactor * (1f + weights.noise * (rng.nextFloat() - 0.5f))
        }
        candidates.sortByDescending { it.score }

        val commands = ArrayList<Command>()
        for (c in candidates) {
            if (decisions.size >= weights.maxOrders) break
            plan(obs, c, sources, commands)
        }
        return commands
    }

    // ---- fontes ----

    private fun collectSources(obs: Observation): MutableList<Source> {
        val sim = obs.sim
        val list = ArrayList<Source>()
        for (k in 0 until obs.planetCount) {
            val power = obs.near[faction][k]
            if (power <= 0) continue
            val p = sim.planets[k]
            // Reserva para a defesa de um planeta próprio ameaçado.
            val threat = if (p.owner == faction && p.level >= 1) obs.enemyNear(faction, k) + obs.enemyIncoming(faction, k) else 0
            val available = power - min(power, threat)
            if (available > 0) list.add(Source(p.x, p.y, obs.bandRadius(k), available, k))
        }
        for (c in obs.idleClusters[faction]) list.add(Source(c.x, c.y, c.radius, c.power, -1))
        return list
    }

    private fun nearestSourceDistance(sources: List<Source>, x: Float, y: Float, exclude: Int): Float {
        var best = Float.MAX_VALUE
        for (s in sources) {
            if (s.planet == exclude || s.available <= 0) continue
            val d = dist(s.x, s.y, x, y)
            if (d < best) best = d
        }
        return if (best == Float.MAX_VALUE) 5000f else best
    }

    // ---- candidatos ----

    private fun collectCandidates(obs: Observation): MutableList<Candidate> {
        val sim = obs.sim
        val list = ArrayList<Candidate>()
        val leader = leaderRatios(obs)
        for (k in 0 until obs.planetCount) {
            val p = sim.planets[k]
            val enemyThere = obs.enemyNear(faction, k) + obs.enemyIncoming(faction, k)
            val mineThere = obs.near[faction][k] + obs.incoming[faction][k]
            if (p.owner == faction && p.level >= 1) {
                val maxH = Rules.maxHealth(p.level)
                if (enemyThere > mineThere) {
                    list += Candidate(DecisionKind.DEFEND, k, enemyThere - mineThere + 10, weights.defend * (60f + 40f * p.level))
                } else if (p.health < maxH) {
                    list += Candidate(DecisionKind.REPAIR, k, maxH - p.health, weights.defend * 0.6f * (40f + 20f * p.level))
                } else if (p.level < p.maxLevel) {
                    val need = Rules.growthCost(p.level) - p.growth - obs.incoming[faction][k]
                    if (need > 0) list += Candidate(DecisionKind.UPGRADE, k, need, weights.upgrade * (45f + 25f * p.level))
                }
            } else if (p.level == 0) {
                val claim = if (p.owner == faction) Rules.growthCost(0) - p.growth else Rules.growthCost(0) + p.growth
                val need = claim + enemyThere - obs.incoming[faction][k]
                if (need > 0) list += Candidate(DecisionKind.EXPAND, k, need, weights.expand * (40f + 30f * p.maxLevel))
            } else if (p.owner != NO_FACTION) {
                val need = p.health + enemyThere - obs.incoming[faction][k]
                if (need > 0) {
                    val bias = 1f + weights.targetLeaderBias * (leader[p.owner] - 1f)
                    list += Candidate(DecisionKind.ATTACK, k, need, weights.attack * (50f + 40f * p.level) * bias)
                }
            }
        }
        return list
    }

    /** Força de cada facção dividida pela média das outras vivas, limitada a [0.5, 2]. */
    private fun leaderRatios(obs: Observation): FloatArray {
        val sim = obs.sim
        val ratios = FloatArray(obs.factionCount) { 1f }
        var sum = 0f
        var alive = 0
        for (f in sim.factions) if (!f.eliminated && f.id != faction) { sum += sim.totalPower(f.id); alive++ }
        if (alive == 0 || sum <= 0f) return ratios
        val avg = sum / alive
        for (f in sim.factions) ratios[f.id] = (sim.totalPower(f.id) / avg).coerceIn(0.5f, 2f)
        return ratios
    }

    // ---- planejamento ----

    private fun plan(obs: Observation, c: Candidate, sources: MutableList<Source>, out: MutableList<Command>) {
        val target = obs.sim.planets[c.planet]
        val required = (c.need * weights.safetyMargin).toInt().coerceAtLeast(1)
        val ordered = sources.filter { it.available > 0 && it.planet != c.planet }
            .sortedBy { dist(it.x, it.y, target.x, target.y) }
            .take(weights.maxSources)
        var total = 0
        for (s in ordered) total += s.available
        if (total < required) return

        var remaining = required
        val points = FloatArray(ordered.size * 2)
        var used = 0
        for (s in ordered) {
            if (remaining <= 0) break
            val take = min(s.available, remaining)
            out += SendUnits(faction, s.x, s.y, s.radius, Target.PlanetRef(c.planet), maxPower = take)
            s.available -= take
            remaining -= take
            points[used * 2] = s.x
            points[used * 2 + 1] = s.y
            used++
        }
        decisions += Decision(c.kind, c.planet, c.score, c.need, required - max(0, remaining), points.copyOf(used * 2))
    }

    private fun dist(x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        /** ref: MECH-007 (a IA original começa no tick 256). */
        const val AI_START_TICK = 256
    }
}
