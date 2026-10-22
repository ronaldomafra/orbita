package dev.orbita.sim

/**
 * Unidades em arrays paralelos (struct of arrays). Slots livres são reaproveitados em ordem LIFO,
 * o que mantém a simulação determinística. Os arrays são expostos para leitura direta pela
 * renderização e pela IA e não devem ser modificados fora da simulação.
 */
class UnitStore(initialCapacity: Int = 1024) {

    var capacity = initialCapacity
        private set

    /** Os slots válidos estão em [0, highWater); verificar [alive]. */
    var highWater = 0
        private set

    var alive = BooleanArray(capacity); private set
    var owner = IntArray(capacity); private set
    var power = IntArray(capacity); private set
    var x = FloatArray(capacity); private set
    var y = FloatArray(capacity); private set
    var mode = IntArray(capacity); private set
    var targetX = FloatArray(capacity); private set
    var targetY = FloatArray(capacity); private set
    var targetPlanet = IntArray(capacity); private set
    var home = IntArray(capacity); private set
    var orbitX = FloatArray(capacity); private set
    var orbitY = FloatArray(capacity); private set
    var orbitCos = FloatArray(capacity); private set
    var orbitSin = FloatArray(capacity); private set
    /** Inimigo travado (trava mútua) ou -1. */
    var lock = IntArray(capacity); private set

    private var freeSlots = IntArray(capacity)
    private var freeCount = 0

    internal fun allocate(): Int {
        val slot = if (freeCount > 0) freeSlots[--freeCount] else {
            if (highWater == capacity) grow()
            highWater++
        }
        alive[slot] = true
        lock[slot] = -1
        home[slot] = -1
        targetPlanet[slot] = -1
        mode[slot] = MODE_HOLD
        return slot
    }

    internal fun release(slot: Int) {
        alive[slot] = false
        lock[slot] = -1
        freeSlots[freeCount++] = slot
    }

    private fun grow() {
        capacity *= 2
        alive = alive.copyOf(capacity)
        owner = owner.copyOf(capacity)
        power = power.copyOf(capacity)
        x = x.copyOf(capacity)
        y = y.copyOf(capacity)
        mode = mode.copyOf(capacity)
        targetX = targetX.copyOf(capacity)
        targetY = targetY.copyOf(capacity)
        targetPlanet = targetPlanet.copyOf(capacity)
        home = home.copyOf(capacity)
        orbitX = orbitX.copyOf(capacity)
        orbitY = orbitY.copyOf(capacity)
        orbitCos = orbitCos.copyOf(capacity)
        orbitSin = orbitSin.copyOf(capacity)
        lock = lock.copyOf(capacity)
        freeSlots = freeSlots.copyOf(capacity)
    }

    companion object {
        /** Parada no lugar (ordem cumprida ou cancelada). */
        const val MODE_HOLD = 0
        /** Orbitando [home]. */
        const val MODE_ORBIT = 1
        const val MODE_MOVE_POINT = 2
        const val MODE_MOVE_PLANET = 3
    }
}
