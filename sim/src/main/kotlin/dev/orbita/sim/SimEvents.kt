package dev.orbita.sim

/**
 * Eventos do último tick, para efeitos visuais e sonoros. Só escrita pela simulação:
 * não entra no estado nem no [Simulation.stateHash]. É limpo no início de cada [Simulation.step].
 */
class SimEvents {
    var count = 0
        private set

    var type = IntArray(256); private set
    var x = FloatArray(256); private set
    var y = FloatArray(256); private set
    var faction = IntArray(256); private set
    /** Power envolvido (ex.: power da unidade morta em [DEATH]). */
    var amount = IntArray(256); private set

    internal fun clear() {
        count = 0
    }

    internal fun add(type: Int, x: Float, y: Float, faction: Int, amount: Int = 0) {
        if (count == this.type.size) grow()
        this.type[count] = type
        this.x[count] = x
        this.y[count] = y
        this.faction[count] = faction
        this.amount[count] = amount
        count++
    }

    private fun grow() {
        val n = type.size * 2
        type = type.copyOf(n); x = x.copyOf(n); y = y.copyOf(n); faction = faction.copyOf(n); amount = amount.copyOf(n)
    }

    companion object {
        const val CLASH = 0
        /** Planeta ativado (nível 0 → 1) por [faction]. */
        const val CAPTURE = 1
        const val LEVEL_UP = 2
        const val DESTROYED = 3
        /** Unidade abatida em combate ([amount] = power perdido). AJ-08 */
        const val DEATH = 4
    }
}
