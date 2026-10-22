package dev.orbita.arena

import dev.orbita.ai.BotDriver
import dev.orbita.ai.Personalities
import dev.orbita.ai.UtilityBot
import dev.orbita.sim.NO_FACTION
import dev.orbita.sim.Simulation
import dev.orbita.sim.StandardMaps
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Partidas bot × bot headless (F6.5). Uso: `./gradlew :tools:arena:run --args="<partidas> <minutos>"`.
 * As personalidades fazem rodízio pelas 4 posições para neutralizar a vantagem de posição.
 */
data class MatchResult(
    val index: Int,
    val seats: List<String>,
    val winnerSeat: Int,
    val leaderSeat: Int,
    val ticks: Int,
    val hash: Long,
)

fun playMatch(index: Int, maxTicks: Int): MatchResult {
    val seed = 1000L + index
    val sim = Simulation(StandardMaps.quad4(), seed)
    val personalities = (0 until 4).map { seat -> Personalities.ALL[(seat + index) % Personalities.ALL.size] }
    val driver = BotDriver(sim, personalities.mapIndexed { seat, w -> UtilityBot(seat, w, seed) })
    while (sim.tick < maxTicks && sim.winner == NO_FACTION) {
        driver.beforeStep()
        sim.step()
    }
    val leader = (0 until 4).maxByOrNull { sim.totalPower(it) + 200 * sim.planets.count { p -> p.owner == it && p.level >= 1 } } ?: 0
    return MatchResult(index, personalities.map { it.name }, sim.winner, leader, sim.tick, sim.stateHash())
}

fun main(args: Array<String>) {
    val matches = args.getOrNull(0)?.toIntOrNull() ?: 200
    val minutes = args.getOrNull(1)?.toIntOrNull() ?: 15
    val maxTicks = minutes * 60 * 60
    val threads = Runtime.getRuntime().availableProcessors()
    val pool = Executors.newFixedThreadPool(threads)

    val start = System.nanoTime()
    val futures: List<Future<MatchResult>> = (0 until matches).map { i -> pool.submit<MatchResult> { playMatch(i, maxTicks) } }
    val results = futures.map { it.get() }
    pool.shutdown()
    val seconds = (System.nanoTime() - start) / 1e9

    val decided = results.filter { it.winnerSeat != NO_FACTION }
    val timeouts = results.size - decided.size
    println("Arena: $matches partidas, limite $minutes min de jogo, $threads threads, %.1f s".format(seconds))
    println("Decididas: ${decided.size}  |  Tempo esgotado: $timeouts")
    if (decided.isNotEmpty()) {
        println("Duração média das decididas: %.1f min".format(decided.map { it.ticks }.average() / 3600))
    }

    println("\nVitórias por personalidade (decididas) e liderança no tempo esgotado:")
    for (p in Personalities.ALL) {
        val seatsPlayed = results.sumOf { r -> r.seats.count { it == p.name } }
        val wins = decided.count { it.seats[it.winnerSeat] == p.name }
        val leads = results.filter { it.winnerSeat == NO_FACTION }.count { it.seats[it.leaderSeat] == p.name }
        println("  %-14s vitórias %4d  (%.1f%% por assento jogado)   lidera no timeout %4d".format(
            p.name, wins, 100.0 * wins / seatsPlayed.coerceAtLeast(1), leads))
    }

    println("\nVitórias por posição (viés do mapa):")
    for (seat in 0 until 4) println("  posição $seat: ${decided.count { it.winnerSeat == seat }}")
}
