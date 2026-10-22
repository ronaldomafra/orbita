package dev.orbita.game.match

import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.TimeUtils
import com.badlogic.gdx.utils.viewport.Viewport
import kotlin.math.hypot

/**
 * Gestos da partida (spec §4):
 * - 1 dedo / botão esquerdo arrastando: laço circular de seleção (centro = início, raio = distância);
 * - toque: [Listener.onTap] (ordem, ou seleção por toque duplo);
 * - 2 dedos: pan + pinça; no desktop, botão direito arrastando = pan e roda = zoom.
 */
class MatchInput(
    private val viewport: Viewport,
    private val camera: OrthographicCamera,
    private val worldWidth: Float,
    private val worldHeight: Float,
    private val listener: Listener,
) : InputAdapter() {

    interface Listener {
        fun onLasso(centerX: Float, centerY: Float, radius: Float, finished: Boolean)
        fun onLassoCancel()
        fun onTap(worldX: Float, worldY: Float, doubleTap: Boolean)
    }

    private val tmp = Vector2()
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

    private fun panBy(dxScreen: Float, dyScreen: Float) {
        val scale = camera.zoom * viewport.worldWidth / viewport.screenWidth
        camera.position.x -= dxScreen * scale
        camera.position.y += dyScreen * scale
        clampCamera()
    }

    private fun zoomBy(factor: Float) {
        camera.zoom = (camera.zoom * factor).coerceIn(MIN_ZOOM, 1f)
        clampCamera()
    }

    private fun clampCamera() {
        val halfW = viewport.worldWidth * camera.zoom / 2
        val halfH = viewport.worldHeight * camera.zoom / 2
        camera.position.x = camera.position.x.coerceIn(halfW, worldWidth - halfW)
        camera.position.y = camera.position.y.coerceIn(halfH, worldHeight - halfH)
        camera.update()
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
        viewport.unproject(tmp.set(screenX.toFloat(), screenY.toFloat()))
    }

    companion object {
        private const val MAX_POINTERS = 10
        private const val TAP_SLOP_PX = 18f
        private const val DOUBLE_TAP_MS = 320L
        private const val DOUBLE_TAP_PX = 60f
        private const val MIN_ZOOM = 0.45f
    }
}
