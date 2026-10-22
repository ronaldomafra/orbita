package dev.orbita.android

import android.app.Activity
import android.os.Bundle
import android.util.Log
import dev.orbita.sim.DeterminismProbe
import dev.orbita.sim.PerfProbe

/**
 * Diagnóstico headless da simulação no aparelho (T-10 / T-11), sem libGDX:
 * adb shell am start -n dev.orbita.spike/dev.orbita.android.ProbeActivity --el seed 12345
 */
class ProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seed = intent.getLongExtra("seed", 12345L)
        Thread {
            val result = DeterminismProbe.run(seed)
            Log.i(TAG, "seed=$seed finalHash=${result.finalHash} checkpoints=${result.checkpoints.hashCode()}")
            val perf = PerfProbe.run()
            val phases = perf.phaseMs.joinToString("/") { "%.2f".format(it) }
            Log.i(TAG, "perf peakUnits=${perf.peakUnits} msPerTick=%.3f worstMs=%.3f phases=%s".format(perf.msPerTick, perf.worstMs, phases))
            runOnUiThread { finish() }
        }.start()
    }

    companion object {
        const val TAG = "ORBITA_PROBE"
    }
}
