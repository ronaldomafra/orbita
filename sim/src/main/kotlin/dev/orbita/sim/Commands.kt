package dev.orbita.sim

sealed interface Target {
    data class Point(val x: Float, val y: Float) : Target
    data class PlanetRef(val planetId: Int) : Target
}

/** Entrada da simulação (spec R8). Jogador e bots usam exatamente os mesmos comandos. */
sealed interface Command {
    val faction: Int
}

/**
 * Envia as unidades de [faction] dentro do círculo ([fromX], [fromY], [radius]) para [target],
 * em ordem de slot, até somar [maxPower].
 */
data class SendUnits(
    override val faction: Int,
    val fromX: Float,
    val fromY: Float,
    val radius: Float,
    val target: Target,
    val maxPower: Int = Int.MAX_VALUE,
) : Command

/** Comando aplicado no início do tick [tick]; a lista ordenada destes forma o replay. */
data class TimedCommand(val tick: Int, val command: Command)
