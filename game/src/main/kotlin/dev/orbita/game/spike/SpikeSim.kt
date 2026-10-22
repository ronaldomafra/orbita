package dev.orbita.game.spike

import com.badlogic.gdx.math.MathUtils

/**
 * Carga sintética do spike F5: partículas orbitando 12 âncoras e viajando em grupos entre elas,
 * com busca de vizinho mais próximo de outra facção em grade espacial por facção.
 * Não contém regras de jogo; serve só para medir custo de simulação e renderização.
 *
 * Estado em arrays paralelos (struct of arrays) e nenhuma alocação por tick.
 */
class SpikeSim(val n: Int, seed: Long) {

    val anchorX = FloatArray(ANCHORS)
    val anchorY = FloatArray(ANCHORS)

    val x = FloatArray(n)
    val y = FloatArray(n)
    val faction = IntArray(n)
    val anchor = IntArray(n)
    /** Âncora de destino ou -1 quando orbitando. */
    val target = IntArray(n) { -1 }
    val nearestEnemy = IntArray(n) { -1 }

    private val angle = FloatArray(n)
    private val orbitRadius = FloatArray(n)
    private val angularSpeed = FloatArray(n)

    private val cols = (WORLD_W / CELL).toInt() + 1
    private val rows = (WORLD_H / CELL).toInt() + 1
    private val cellCount = cols * rows
    /** Cabeça da lista encadeada de cada célula, separada por facção. */
    private val cellHead = IntArray(FACTIONS * cellCount)
    private val cellNext = IntArray(n)
    private val cellOf = IntArray(n)

    private var rng = (seed xor 0x5DEECE66DL).toInt().let { if (it == 0) 1 else it }
    var tick = 0
        private set

    init {
        for (i in 0 until ANCHORS) {
            anchorX[i] = 200f + (i % 4) * 400f
            anchorY[i] = 130f + (i / 4) * 250f
        }
        for (i in 0 until n) {
            val a = i % ANCHORS
            anchor[i] = a
            faction[i] = a % FACTIONS
            angle[i] = nextFloat() * MathUtils.PI2
            orbitRadius[i] = 30f + nextFloat() * 30f
            angularSpeed[i] = (0.6f + nextFloat() * 0.6f) / TICK_HZ
            x[i] = anchorX[a] + MathUtils.cos(angle[i]) * orbitRadius[i]
            y[i] = anchorY[a] + MathUtils.sin(angle[i]) * orbitRadius[i]
        }
    }

    fun step() {
        move()
        rebuildGrid()
        findNearestEnemies()
        if (tick % SEND_PERIOD_TICKS == 0) sendRandomGroup()
        tick++
    }

    /** Envia metade das partículas que orbitam [from] para [to]. */
    fun send(from: Int, to: Int) {
        if (from == to) return
        var toggle = false
        for (i in 0 until n) {
            if (anchor[i] == from && target[i] < 0) {
                toggle = !toggle
                if (toggle) target[i] = to
            }
        }
    }

    fun nearestAnchor(px: Float, py: Float): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (a in 0 until ANCHORS) {
            val dx = anchorX[a] - px
            val dy = anchorY[a] - py
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = a }
        }
        return best
    }

    fun checksum(): Long {
        var h = 1125899906842597L
        for (i in 0 until n) {
            h = 31 * h + x[i].toRawBits()
            h = 31 * h + y[i].toRawBits()
        }
        return h
    }

    private fun move() {
        for (i in 0 until n) {
            val t = target[i]
            if (t < 0) {
                val a = anchor[i]
                angle[i] += angularSpeed[i]
                x[i] = anchorX[a] + MathUtils.cos(angle[i]) * orbitRadius[i]
                y[i] = anchorY[a] + MathUtils.sin(angle[i]) * orbitRadius[i]
            } else {
                val dx = anchorX[t] - x[i]
                val dy = anchorY[t] - y[i]
                val d2 = dx * dx + dy * dy
                val r = orbitRadius[i]
                if (d2 <= r * r) {
                    anchor[i] = t
                    target[i] = -1
                    angle[i] = MathUtils.atan2(-dy, -dx)
                } else {
                    val inv = SPEED_PER_TICK / Math.sqrt(d2.toDouble()).toFloat()
                    x[i] += dx * inv
                    y[i] += dy * inv
                }
            }
        }
    }

    private fun rebuildGrid() {
        cellHead.fill(-1)
        for (i in 0 until n) {
            val cx = (x[i] / CELL).toInt().coerceIn(0, cols - 1)
            val cy = (y[i] / CELL).toInt().coerceIn(0, rows - 1)
            val cell = cy * cols + cx
            cellOf[i] = cell
            val slot = faction[i] * cellCount + cell
            cellNext[i] = cellHead[slot]
            cellHead[slot] = i
        }
    }

    private fun findNearestEnemies() {
        val range2 = CELL * CELL
        // Escalonado: cada unidade refaz a busca a cada SCAN_PERIOD ticks (o original faz algo análogo, MECH-004).
        val phase = tick and (SCAN_PERIOD - 1)
        for (i in 0 until n) {
            if ((i and (SCAN_PERIOD - 1)) != phase) continue
            val cell = cellOf[i]
            val cx = cell % cols
            val cy = cell / cols
            val f = faction[i]
            var best = -1
            var bestD = range2
            for (of in 0 until FACTIONS) {
                if (of == f) continue
                val base = of * cellCount
                for (ny in maxOf(0, cy - 1)..minOf(rows - 1, cy + 1)) {
                    for (nx in maxOf(0, cx - 1)..minOf(cols - 1, cx + 1)) {
                        var j = cellHead[base + ny * cols + nx]
                        while (j >= 0) {
                            val dx = x[j] - x[i]
                            val dy = y[j] - y[i]
                            val d = dx * dx + dy * dy
                            if (d < bestD) { bestD = d; best = j }
                            j = cellNext[j]
                        }
                    }
                }
            }
            nearestEnemy[i] = best
        }
    }

    private fun sendRandomGroup() {
        val from = nextInt(ANCHORS)
        var to = nextInt(ANCHORS)
        if (to == from) to = (to + 1) % ANCHORS
        send(from, to)
    }

    private fun nextInt(bound: Int): Int {
        rng = rng xor (rng shl 13)
        rng = rng xor (rng ushr 17)
        rng = rng xor (rng shl 5)
        return (rng ushr 1) % bound
    }

    private fun nextFloat(): Float = nextInt(1 shl 24) / (1 shl 24).toFloat()

    companion object {
        const val WORLD_W = 1600f
        const val WORLD_H = 760f
        const val ANCHORS = 12
        const val FACTIONS = 4
        const val TICK_HZ = 60f
        const val CELL = 40f
        const val SPEED_PER_TICK = 150f / TICK_HZ
        const val SEND_PERIOD_TICKS = 128
        /** Potência de 2. */
        const val SCAN_PERIOD = 4
    }
}
