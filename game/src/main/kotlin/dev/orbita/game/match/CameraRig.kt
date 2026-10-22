package dev.orbita.game.match

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * Câmera 3D sobre o plano do mapa (z = 0). Com o mapa inteiro na tela a vista é de cima; ao aproximar,
 * a câmera inclina até [MAX_PITCH_DEG] e o mapa ganha perspectiva (REF-06). O jogo continua 2D no plano.
 */
class CameraRig(private val worldWidth: Float, private val worldHeight: Float) {

    val camera = PerspectiveCamera(FOV_DEG, 1f, 1f)

    /** Ponto do plano no centro da tela. */
    var targetX = worldWidth / 2
    var targetY = worldHeight / 2

    /** 1 = mapa inteiro; < 1 aproxima (e inclina); > 1 afasta. */
    var zoom = 1f
        set(value) { field = value.coerceIn(MIN_ZOOM, MAX_ZOOM) }

    /** Inclinação atual em radianos (0 = vista de cima). */
    var pitch = 0f
        private set

    /** Fator vertical para desenhar objetos "em pé" (esferas, unidades) sem achatá-los com a inclinação. */
    val upright: Float get() = 1f / cos(pitch)

    private var fitDistance = 1f
    private var focalPx = 1f
    private val tmp = Vector3()

    /** Projeção ortográfica em pixels da tela, para objetos "em pé" desenhados após [project]. */
    val screenMatrix = Matrix4()

    /** Resultado do último [project]: posição na tela (px, origem embaixo à esquerda) e pixels por unidade de mapa. */
    var screenX = 0f
        private set
    var screenY = 0f
        private set
    var pixelsPerUnit = 0f
        private set

    fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        val tanHalf = tan(FOV_DEG * MathUtils.degreesToRadians / 2)
        val aspect = camera.viewportWidth / camera.viewportHeight
        fitDistance = max(worldHeight / 2 / tanHalf, worldWidth / 2 / (tanHalf * aspect)) * FIT_MARGIN
        focalPx = camera.viewportHeight / 2 / tanHalf
        screenMatrix.setToOrtho2D(0f, 0f, camera.viewportWidth, camera.viewportHeight)
        update()
    }

    fun update() {
        val closeness = ((1f - zoom) / (1f - MIN_ZOOM)).coerceIn(0f, 1f)
        pitch = MAX_PITCH_DEG * MathUtils.degreesToRadians * closeness * closeness * (3f - 2f * closeness)
        val d = fitDistance * zoom
        val s = sin(pitch)
        val c = cos(pitch)
        camera.position.set(targetX, targetY - d * s, d * c)
        camera.direction.set(0f, s, -c)
        camera.up.set(0f, c, s)
        camera.near = d * 0.05f
        camera.far = d * 6f
        camera.update()
    }

    /**
     * Projeta o ponto (x, y) do plano na tela e calcula a escala de perspectiva naquela profundidade.
     * Retorna false se o ponto estiver atrás da câmera.
     */
    fun project(x: Float, y: Float): Boolean {
        val p = camera.position
        val d = camera.direction
        val depth = (x - p.x) * d.x + (y - p.y) * d.y + (0f - p.z) * d.z
        if (depth <= camera.near) return false
        camera.project(tmp.set(x, y, 0f))
        screenX = tmp.x
        screenY = tmp.y
        pixelsPerUnit = focalPx / depth
        return true
    }

    /** Converte um ponto da tela no ponto do plano do mapa (raio da câmera ∩ z = 0). */
    fun pick(screenX: Float, screenY: Float, out: Vector2): Vector2 {
        val ray = camera.getPickRay(screenX, screenY)
        val dz = ray.direction.z
        if (dz > -1e-5f) return out.set(targetX, targetY)
        val t = -ray.origin.z / dz
        return out.set(ray.origin.x + ray.direction.x * t, ray.origin.y + ray.direction.y * t)
    }

    /** Meia largura/altura aproximada da área visível no plano (para os limites de pan). */
    fun visibleHalfWidth(): Float = worldWidth / 2 * zoom
    fun visibleHalfHeight(): Float = worldHeight / 2 * zoom

    companion object {
        const val MIN_ZOOM = 0.42f
        const val MAX_ZOOM = 2.2f
        private const val FOV_DEG = 40f
        private const val MAX_PITCH_DEG = 42f
        private const val FIT_MARGIN = 1.02f
    }
}
