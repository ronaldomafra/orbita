package dev.orbita.game.match

import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.TimeUtils
import kotlin.math.hypot

/**
 * Gestos da partida (spec §4):
 * - 1 dedo / botão esquerdo arrastando: laço circular de seleção (centro = início, raio = distância);
 * - toque: [Listener.onTap] (ordem, ou seleção por toque duplo);
 * - 2 dedos: pan + pinça; no desktop, botão direito arrastando = pan e roda = zoom.
 * A câmera pode passar das bordas com resistência e volta sozinha ao soltar (AJ-01); zoom amplo (AJ-10)
 * e, ao aproximar, inclina em perspectiva ([CameraRig], REF-06).
 */
class MatchInput(
    private val rig: CameraRig,
    private val worldWidth: Float,
    private val worldHeight: Float,
    private val listener: Listener,
) : InputAdapter() {

    interface Listener {
        fun onLasso(centerX: Float, centerY: Float, radius: Float, finished: Boolean)
        fun onLassoCancel()
        fun onTap(worldX: Float, worldY: Float, doubleTap: Boolean)
    }

    /** Resistência ao arrastar além da borda (1 = sem resistência). */
    var edgeResistance = 0.45f
    /** Velocidade da mola de retorno à área de repouso. */
    var returnRate = 6f

    private val tmp = Vector2()
    private val tmpA = Vector2()
    private val tmpB = Vector2()
    private val pointerX = FloatArray(MAX_POINTERS)
    private val pointerY = FloatArray(MAX_POINTERS)
    private val pointerDown = BooleanArray(MAX_POINTERS)

    private var lassoPointer = -1
    private var lassoStartScreenX = 0f
    private var lassoStartScreenY = 0f
    private var lassoStartWorldX = 0f
    private var lassoStartWorldY = 0f
    private var lassoMoved = false

    private var panning = false
    private var lastPinchDistance = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f

    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer >= MAX_POINTERS) return false
        pointerDown[pointer] = true
        pointerX[pointer] = screenX.toFloat()
        pointerY[pointer] = screenY.toFloat()

        if (button == Input.Buttons.RIGHT) {
            panning = true
            lastMidX = screenX.toFloat(); lastMidY = screenY.toFloat()
            return true
        }
        if (activePointers() >= 2) {
            cancelLasso()
            startTwoFinger()
            return true
        }
        lassoPointer = pointer
        lassoStartScreenX = screenX.toFloat()
        lassoStartScreenY = screenY.toFloat()
        toWorld(screenX, screenY)
        lassoStartWorldX = tmp.x
        lassoStartWorldY = tmp.y
        lassoMoved = false
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (pointer >= MAX_POINTERS) return false
        pointerX[pointer] = screenX.toFloat()
        pointerY[pointer] = screenY.toFloat()

        if (panning && activePointers() == 1 && lassoPointer < 0) {
            panBy(screenX - lastMidX, screenY - lastMidY)
            lastMidX = screenX.toFloat(); lastMidY = screenY.toFloat()
            return true
        }
        if (activePointers() >= 2) {
            twoFingerUpdate()
            return true
        }
        if (pointer == lassoPointer) {
            if (!lassoMoved && hypot(screenX - lassoStartScreenX, screenY - lassoStartScreenY) > TAP_SLOP_PX) lassoMoved = true
            if (lassoMoved) {
                toWorld(screenX, screenY)
                listener.onLasso(lassoStartWorldX, lassoStartWorldY, hypot(tmp.x - lassoStartWorldX, tmp.y - lassoStartWorldY), false)
            }
        }
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (pointer >= MAX_POINTERS) return false
        pointerDown[pointer] = false
        if (button == Input.Buttons.RIGHT || (panning && activePointers() == 0)) {
            panning = false
            return true
        }
        if (pointer != lassoPointer) return true
        lassoPointer = -1
        toWorld(screenX, screenY)
        if (lassoMoved) {
            listener.onLasso(lassoStartWorldX, lassoStartWorldY, hypot(tmp.x - lassoStartWorldX, tmp.y - lassoStartWorldY), true)
        } else {
            val now = TimeUtils.millis()
            val double = now - lastTapTime < DOUBLE_TAP_MS && hypot(screenX - lastTapX, screenY - lastTapY) < DOUBLE_TAP_PX
            listener.onTap(tmp.x, tmp.y, double)
            lastTapTime = if (double) 0L else now
            lastTapX = screenX.toFloat(); lastTapY = screenY.toFloat()
        }
        return true
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        zoomBy(1f + amountY * 0.1f)
        return true
    }

    private fun cancelLasso() {
        if (lassoPointer >= 0 && lassoMoved) listener.onLassoCancel()
        lassoPointer = -1
        lassoMoved = false
    }

    private fun startTwoFinger() {
        val (a, b) = firstTwoPointers() ?: return
        lastPinchDistance = hypot(pointerX[a] - pointerX[b], pointerY[a] - pointerY[b])
        lastMidX = (pointerX[a] + pointerX[b]) / 2
        lastMidY = (pointerY[a] + pointerY[b]) / 2
    }

    private fun twoFingerUpdate() {
        val (a, b) = firstTwoPointers() ?: return
        val distance = hypot(pointerX[a] - pointerX[b], pointerY[a] - pointerY[b])
        val midX = (pointerX[a] + pointerX[b]) / 2
        val midY = (pointerY[a] + pointerY[b]) / 2
        if (lastPinchDistance > 0f && distance > 0f) zoomBy(lastPinchDistance / distance)
        panBy(midX - lastMidX, midY - lastMidY)
        lastPinchDistance = distance
        lastMidX = midX; lastMidY = midY
    }

    /**
     * Pan com resistência fora da área "de repouso" (sensação de espaço infinito com mola; AJ-01).
     * Usa a diferença entre os pontos do plano sob o dedo: exato também com a câmera inclinada.
     */
    private fun panBy(dxScreen: Float, dyScreen: Float) {
        rig.pick(lastMidX, lastMidY, tmpA)
        rig.pick(lastMidX + dxScreen, lastMidY + dyScreen, tmpB)
        val outside = softOffsetX() != 0f || softOffsetY() != 0f
        val resistance = if (outside) edgeResistance else 1f
        rig.targetX -= (tmpB.x - tmpA.x) * resistance
        rig.targetY -= (tmpB.y - tmpA.y) * resistance
        hardClamp()
    }

    private fun zoomBy(factor: Float) {
        rig.zoom = rig.zoom * factor
        hardClamp()
    }

    /** Chamar a cada frame: sem dedos na tela, a câmera volta suavemente para a área de repouso. */
    fun update(delta: Float) {
        if (activePointers() > 0) return
        val k = (returnRate * delta).coerceAtMost(1f)
        val ox = softOffsetX()
        val oy = softOffsetY()
        if (ox == 0f && oy == 0f) return
        rig.targetX -= ox * k
        rig.targetY -= oy * k
        rig.update()
    }

    /** Quanto o centro da câmera está além da área de repouso no eixo X (0 = dentro). */
    private fun softOffsetX(): Float = offset(rig.targetX, rig.visibleHalfWidth(), worldWidth)

    private fun softOffsetY(): Float = offset(rig.targetY, rig.visibleHalfHeight(), worldHeight)

    private fun offset(center: Float, half: Float, size: Float): Float {
        val (lo, hi) = if (2 * half >= size) size / 2 - SOFT_MARGIN to size / 2 + SOFT_MARGIN
        else half - SOFT_MARGIN to size - half + SOFT_MARGIN
        return when {
            center < lo -> center - lo
            center > hi -> center - hi
            else -> 0f
        }
    }

    /** Limite rígido: no máximo meia tela além da área de repouso. */
    private fun hardClamp() {
        val ox = softOffsetX()
        val oy = softOffsetY()
        val maxX = rig.visibleHalfWidth() * 2 * HARD_EXTRA
        val maxY = rig.visibleHalfHeight() * 2 * HARD_EXTRA
        if (ox > maxX) rig.targetX -= ox - maxX else if (ox < -maxX) rig.targetX -= ox + maxX
        if (oy > maxY) rig.targetY -= oy - maxY else if (oy < -maxY) rig.targetY -= oy + maxY
        rig.update()
    }

    private fun activePointers(): Int = pointerDown.count { it }

    private fun firstTwoPointers(): Pair<Int, Int>? {
        var a = -1
        for (i in 0 until MAX_POINTERS) if (pointerDown[i]) {
            if (a < 0) a = i else return a to i
        }
        return null
    }

    private fun toWorld(screenX: Int, screenY: Int) {
        rig.pick(screenX.toFloat(), screenY.toFloat(), tmp)
    }

    companion object {
        private const val MAX_POINTERS = 10
        private const val TAP_SLOP_PX = 18f
        private const val DOUBLE_TAP_MS = 320L
        private const val DOUBLE_TAP_PX = 60f
        private const val SOFT_MARGIN = 120f
        private const val HARD_EXTRA = 0.5f
    }
}
