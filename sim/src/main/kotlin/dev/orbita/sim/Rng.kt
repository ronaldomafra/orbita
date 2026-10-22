package dev.orbita.sim

/** splitmix64: determinístico e idêntico em qualquer JVM/ART. */
class Rng(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += -7046029254386353131L // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -7723592293110705685L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 33) % bound).toInt()
    }

    /** Em [0, 1). */
    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24).toFloat()
}
