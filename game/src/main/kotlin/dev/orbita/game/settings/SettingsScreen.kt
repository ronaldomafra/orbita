package dev.orbita.game.settings

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import dev.orbita.game.ui.UiSkin
import ktx.app.KtxScreen
import ktx.app.clearScreen
import java.util.Locale

/**
 * Menu de ajustes: categorias à esquerda e, à direita, a lista de parâmetros da categoria,
 * gerada a partir de [Params.all]. Salva ao sair.
 */
class SettingsScreen(
    private val settings: Settings,
    private val onBack: () -> Unit,
    private val onApplyAndRestart: () -> Unit,
) : KtxScreen {

    private val uiScale = (Gdx.graphics.height / 720f).coerceIn(0.8f, 2f)
    private val skin: Skin = UiSkin.create(1.35f * uiScale)
    private val stage = Stage(ScreenViewport())
    private val content = Table()
    private var current = Category.MATCH

    init {
        build()
    }

    private fun build() {
        val pad = 14f * uiScale
        // Table(skin): dentro de apply, `skin` seria o skin (nulo) da própria Table.
        val root = Table(skin).apply {
            setFillParent(true)
            background = this@SettingsScreen.skin.getDrawable("panel")
            pad(pad)
        }
        stage.addActor(root)

        // Barra superior.
        val top = Table()
        top.add(Label("Ajustes", skin, "accent")).left().expandX()
        top.add(button("Padrões") { settings.resetCategory(current); showCategory(current) }).padRight(pad)
        top.add(button("Aplicar e reiniciar") { leave(); onApplyAndRestart() }).padRight(pad)
        top.add(button("Voltar") { leave(); onBack() })
        root.add(top).colspan(2).fillX().padBottom(pad).row()

        // Categorias.
        val categories = Table()
        val group = ButtonGroup<TextButton>()
        for (c in Category.entries) {
            val b = TextButton(c.title, skin).apply { pad(10f * uiScale, 18f * uiScale, 10f * uiScale, 18f * uiScale) }
            b.addListener(changed { if (b.isChecked) showCategory(c) })
            group.add(b)
            categories.add(b).fillX().padBottom(8f * uiScale).row()
        }
        root.add(categories).top().width(230f * uiScale).padRight(pad)

        val scroll = ScrollPane(content, skin).apply {
            setScrollingDisabled(true, false)
            setFadeScrollBars(false)
            setOverscroll(false, false)
            setCancelTouchFocus(false)
        }
        root.add(scroll).expand().fill()
        showCategory(Category.MATCH)
    }

    private fun showCategory(category: Category) {
        current = category
        content.clearChildren()
        val pad = 10f * uiScale
        content.top().left()
        content.add(Label(category.note, skin, "muted")).colspan(3).left().padBottom(pad).row()
        var lastGroup: String? = null
        for (p in Params.all) {
            if (p.category != category) continue
            if (p.group != lastGroup) {
                lastGroup = p.group
                if (p.group != null) content.add(Label(p.group, skin, "accent")).colspan(3).left().padTop(pad * 1.5f).padBottom(pad / 2).row()
            }
            addRow(p)
        }
    }

    private fun addRow(p: Param) {
        val pad = 8f * uiScale
        val label = Label(p.label, skin).apply { setWrap(true) }
        if (p.kind == Kind.BOOL) {
            val box = CheckBox("", skin).apply { isChecked = settings.bool(p) }
            box.addListener(changed { settings[p] = if (box.isChecked) 1f else 0f })
            content.add(label).width(520f * uiScale).left().padBottom(pad)
            content.add(box).left().padBottom(pad).colspan(2).row()
            return
        }
        val value = Label(format(p, settings[p]), skin).apply { setAlignment(Align.right) }
        val slider = Slider(p.min, p.max, p.step, false, skin).apply { this.value = settings[p] }
        slider.addListener(changed {
            settings[p] = slider.value
            value.setText(format(p, settings[p]))
        })
        content.add(label).width(520f * uiScale).left().padBottom(pad).padRight(pad)
        content.add(slider).width(380f * uiScale).height(40f * uiScale).padBottom(pad).padRight(pad)
        content.add(value).width(110f * uiScale).right().padBottom(pad).row()
    }

    private fun format(p: Param, v: Float): String = when (p.kind) {
        Kind.INT -> v.toInt().toString()
        else -> if (p.step >= 1f) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.2f", v)
    }

    private fun button(text: String, action: () -> Unit) = TextButton(text, skin).apply {
        pad(10f * uiScale, 18f * uiScale, 10f * uiScale, 18f * uiScale)
        addListener(changed { action() })
    }

    private fun changed(action: () -> Unit) = object : ChangeListener() {
        override fun changed(event: ChangeEvent?, actor: Actor?) = action()
    }

    private fun leave() {
        settings.save()
    }

    override fun show() {
        showCategory(current)
        Gdx.input.inputProcessor = InputMultiplexer(stage, object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { leave(); onBack(); return true }
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
