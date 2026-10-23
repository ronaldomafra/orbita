package dev.orbita.game.settings

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import dev.orbita.game.ui.UiSkin
import dev.orbita.sim.MapLibrary
import ktx.app.KtxScreen
import ktx.app.clearScreen

/** Lista simples de mapas: tocar num mapa começa uma partida nele. */
class MapsScreen(
    private val settings: Settings,
    private val onBack: () -> Unit,
    private val onSelect: () -> Unit,
) : KtxScreen {

    private val uiScale = (Gdx.graphics.height / 720f).coerceIn(0.8f, 2f)
    private val skin: Skin = UiSkin.create(1.8f * uiScale)
    private val stage = Stage(ScreenViewport())
    private val grid = Table()

    init {
        val pad = 14f * uiScale
        // Table(skin): dentro de apply, `skin` seria o skin (nulo) da própria Table.
        val root = Table(skin).apply {
            setFillParent(true)
            background = this@MapsScreen.skin.getDrawable("panel")
            pad(pad)
        }
        stage.addActor(root)
        val top = Table()
        top.add(Label("Mapas", skin, "accent")).left().expandX()
        top.add(TextButton("Voltar", skin).apply {
            pad(10f * uiScale, 18f * uiScale, 10f * uiScale, 18f * uiScale)
            addListener(changed { onBack() })
        })
        root.add(top).fillX().padBottom(pad).row()
        val scroll = ScrollPane(grid, skin).apply {
            setScrollingDisabled(true, false)
            setOverscroll(false, false)
        }
        root.add(scroll).expand().fill()
    }

    private fun rebuild() {
        grid.clearChildren()
        val pad = 10f * uiScale
        MapLibrary.all.forEachIndexed { i, info ->
            val map = info.build()
            val factions = map.factionCount
            val planets = map.planets.size
            val text = "${info.name}\n$factions facções · $planets planetas\n${info.description}"
            val button = TextButton(text, skin).apply {
                label.setWrap(true)
                label.setAlignment(Align.left)
                pad(14f * uiScale)
                isChecked = info.id == settings.mapId
            }
            button.addListener(changed {
                settings.mapId = info.id
                settings.save()
                onSelect()
            })
            grid.add(button).width(Gdx.graphics.width / 3f - 30f * uiScale).height(170f * uiScale).pad(pad).fill()
            if (i % 3 == 2) grid.row()
        }
    }

    private fun changed(action: () -> Unit) = object : ChangeListener() {
        override fun changed(event: ChangeEvent?, actor: Actor?) = action()
    }

    override fun show() {
        rebuild()
        Gdx.input.inputProcessor = InputMultiplexer(stage, object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { onBack(); return true }
                return false
            }
        })
    }

    override fun render(delta: Float) {
        clearScreen(0f, 0f, 0f)
        stage.act(delta)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun dispose() {
        stage.dispose()
        skin.dispose()
    }
}
