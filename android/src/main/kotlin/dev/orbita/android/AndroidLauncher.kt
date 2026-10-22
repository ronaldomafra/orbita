package dev.orbita.android

import android.os.Bundle
import android.util.Log
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import dev.orbita.game.OrbitaGame
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashRecorder()
        // Spike técnico da F5: adb shell am start ... --ei units 3000 (sem o extra, abre a partida)
        val units = intent.getIntExtra("units", 0)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
        }
        // Overlay de debug da IA: --ez debugAi true
        // Estresse: --ei stress 1500 (unidades iniciais por base)
        initialize(OrbitaGame(units, intent.getBooleanExtra("debugAi", false), intent.getIntExtra("stress", 0)), config)
    }

    /**
     * Grava a pilha de qualquer exceção não tratada (de qualquer thread, inclusive a GLThread)
     * em `files/crashes/` e no logcat (`ORBITA_CRASH`), e depois repassa ao handler padrão.
     * Para ler: `adb shell run-as dev.orbita.spike ls files/crashes`.
     */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val dir = File(filesDir, "crashes").apply { mkdirs() }
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val runtime = Runtime.getRuntime()
                val header = "thread=${thread.name} heapUsedKB=${(runtime.totalMemory() - runtime.freeMemory()) / 1024} " +
                    "heapMaxKB=${runtime.maxMemory() / 1024}\n"
                File(dir, "crash-$stamp.txt").writeText(header + trace)
                Log.e("ORBITA_CRASH", header + trace)
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
