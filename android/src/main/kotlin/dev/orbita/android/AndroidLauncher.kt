package dev.orbita.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import dev.orbita.game.OrbitaGame

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Spike técnico da F5: adb shell am start ... --ei units 3000 (sem o extra, abre a partida)
        val units = intent.getIntExtra("units", 0)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
        }
        // Overlay de debug da IA: --ez debugAi true
        initialize(OrbitaGame(units, intent.getBooleanExtra("debugAi", false)), config)
    }
}
