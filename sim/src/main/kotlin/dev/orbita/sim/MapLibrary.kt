package dev.orbita.sim

import kotlin.math.cos
import kotlin.math.sin

/** Um mapa do jogo: identificador estável, nome exibido, descrição curta e construtor. */
class MapInfo(
    val id: String,
    val name: String,
    val description: String,
    val build: () -> MapDef,
)

/**
 * Mapas próprios do Órbita: formatos clássicos do gênero (anéis, braços, espirais),
 * com geometria nossa e simétrica.
 * Todos usam o mesmo mundo (1600×760). Mapas de 3 facções têm simetria de 120°; a facção 0
 * (jogador) fica sempre embaixo.
 */
object MapLibrary {

    private const val W = 1600f
    private const val H = 760f
    private const val CX = W / 2
    private const val CY = H / 2

    /** Ângulos das bases em mapas de 3 facções: jogador embaixo, bots em cima à direita e à esquerda. */
    private val BASES3 = floatArrayOf(270f, 30f, 150f)

    val all: List<MapInfo> = listOf(
        MapInfo("quadrante", "Quadrante", "4 facções nos cantos; centro disputado") { StandardMaps.quad4() },
        MapInfo("cisao", "Cisão", "Braços até um centro forte") { cisao() },
        MapInfo("explosao", "Explosão", "Anéis concêntricos de planetas") { explosao() },
        MapInfo("massa", "Massa", "Muitos planetas, aglomerado central") { massa() },
        MapInfo("coroa", "Coroa", "Planetas em anel entre as bases") { coroa() },
        MapInfo("juncao", "Junção", "Caminhos que se encontram no centro") { juncao() },
        MapInfo("hexagono", "Hexágono", "Mapa pequeno, briga de perto") { hexagono() },
        MapInfo("projetil", "Projétil", "Pares de planetas à frente de cada base") { projetil() },
        MapInfo("infinito", "Infinito", "4 facções num oito deitado") { infinito() },
        MapInfo("espiral", "Espiral", "Três braços em espiral") { espiral() },
        MapInfo("dardo", "Dardo", "Pontas de flecha apontando ao centro") { dardo() },
        MapInfo("nucleo", "Núcleo", "Três anéis alternados até o núcleo") { nucleo() },
        MapInfo("triade", "Tríade", "Triângulo com arestas disputadas") { triade() },
    )

    fun byId(id: String): MapInfo = all.firstOrNull { it.id == id } ?: all.first()

    // ---- construção ----

    private class Builder(val factions: Int) {
        val planets = ArrayList<PlanetDef>()

        fun base(faction: Int, x: Float, y: Float) {
            planets += PlanetDef(x, y, maxLevel = 3, owner = faction, level = 1, startUnits = 100)
        }

        fun neutral(x: Float, y: Float, maxLevel: Int) {
            planets += PlanetDef(x, y, maxLevel = maxLevel)
        }

        /** Planeta neutro em coordenadas polares em torno do centro. */
        fun polar(r: Float, angleDeg: Float, maxLevel: Int) = neutral(px(r, angleDeg), py(r, angleDeg), maxLevel)

        /** Bases das 3 facções em raio [r], com simetria de 120°. */
        fun bases3(r: Float) = BASES3.forEachIndexed { f, a -> base(f, px(r, a), py(r, a)) }

        /** Para cada facção, um planeta em (r, ângulo da base + [offset]). */
        fun perFaction(r: Float, offset: Float, maxLevel: Int) = BASES3.forEach { a -> polar(r, a + offset, maxLevel) }

        fun center(maxLevel: Int) = neutral(CX, CY, maxLevel)

        fun build() = MapDef(width = W, height = H, factionCount = factions, planets = planets)
    }

    private fun px(r: Float, a: Float) = CX + r * cos(Math.toRadians(a.toDouble())).toFloat()
    private fun py(r: Float, a: Float) = CY + r * sin(Math.toRadians(a.toDouble())).toFloat()

    private fun map3(block: Builder.() -> Unit) = Builder(3).apply(block).build()

    private fun cisao() = map3 {
        bases3(310f)
        perFaction(185f, 0f, 1)
        perFaction(120f, 60f, 2)
        perFaction(250f, 60f, 1)
        center(3)
    }

    private fun explosao() = map3 {
        bases3(310f)
        for (k in 0 until 9) polar(230f, 290f + k * 40f, 1)
        for (k in 0 until 6) polar(115f, 300f + k * 60f, 2)
        center(3)
    }

    private fun massa() = map3 {
        bases3(310f)
        for (k in 0 until 6) polar(115f, 300f + k * 60f, if (k % 2 == 0) 2 else 1)
        for (k in 0 until 9) polar(225f, 290f + k * 40f, 1)
        center(3)
    }

    private fun coroa() = map3 {
        bases3(310f)
        // Três planetas em cada arco entre bases, no mesmo anel.
        for (a in BASES3) for (j in 1..3) polar(310f, a + j * 30f, if (j == 2) 2 else 1)
        center(3)
    }

    private fun juncao() = map3 {
        bases3(310f)
        perFaction(190f, 0f, 1)
        perFaction(140f, 60f, 2)
        center(3)
    }

    private fun hexagono() = map3 {
        bases3(230f)
        perFaction(115f, 60f, 3)
    }

    private fun projetil() = map3 {
        bases3(310f)
        perFaction(215f, 28f, 1)
        perFaction(215f, -28f, 1)
        perFaction(115f, 60f, 2)
        center(3)
    }

    private fun espiral() = map3 {
        bases3(310f)
        for (j in 1..3) perFaction(310f - j * 62f, j * 35f, if (j == 1) 1 else 2)
        center(3)
    }

    private fun dardo() = map3 {
        bases3(310f)
        perFaction(205f, 17f, 1)
        perFaction(205f, -17f, 1)
        perFaction(100f, 0f, 2)
    }

    private fun nucleo() = map3 {
        bases3(310f)
        perFaction(230f, 60f, 1)
        perFaction(160f, 0f, 2)
        perFaction(80f, 60f, 3)
    }

    private fun triade() = map3 {
        bases3(310f)
        // Duas paradas em cada aresta do triângulo formado pelas bases.
        for (i in BASES3.indices) {
            val a = BASES3[i]
            val b = BASES3[(i + 1) % BASES3.size]
            val ax = px(310f, a); val ay = py(310f, a)
            val bx = px(310f, b); val by = py(310f, b)
            for (t in floatArrayOf(1f / 3, 2f / 3)) neutral(ax + (bx - ax) * t, ay + (by - ay) * t, 2)
        }
        center(3)
    }

    /** 4 facções sobre uma lemniscata (oito deitado); centro no cruzamento. */
    private fun infinito(): MapDef {
        val b = Builder(4)
        val a = 700f
        fun x(t: Double) = CX + (a * cos(t) / (1 + sin(t) * sin(t))).toFloat()
        fun y(t: Double) = CY + (a * sin(t) * cos(t) / (1 + sin(t) * sin(t))).toFloat()
        val step = Math.PI / 8
        // Bases: jogador embaixo à esquerda (k = 7), depois sentido horário pelas pontas.
        val baseOrder = intArrayOf(7, 1, 9, 15)
        baseOrder.forEachIndexed { f, k -> b.base(f, x(k * step), y(k * step)) }
        for (k in 0 until 16) {
            if (k in baseOrder || k == 4 || k == 12) continue // 4 e 12 caem no cruzamento
            val level = when (k) { 0, 8 -> 1; 2, 6, 10, 14 -> 2; else -> 1 }
            b.neutral(x(k * step), y(k * step), level)
        }
        b.center(3)
        return b.build()
    }
}
