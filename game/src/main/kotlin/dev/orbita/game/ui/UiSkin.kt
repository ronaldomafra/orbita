package dev.orbita.game.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

/** Skin da interface gerado em código (sem arquivos de assets), no tom do jogo. */
object UiSkin {

    val ACCENT = Color(0.20f, 0.90f, 1.00f, 1f)
    private val TEXT = Color(0.88f, 0.92f, 0.98f, 1f)
    private val MUTED = Color(0.55f, 0.62f, 0.72f, 1f)

    fun create(fontScale: Float): Skin {
        val skin = Skin()
        val font = BitmapFont().apply {
            data.setScale(fontScale)
            regions.forEach { it.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
        }
        skin.add("default", font)

        val panel = rect(Color(0.03f, 0.05f, 0.10f, 0.94f))
        val button = rect(Color(0.10f, 0.14f, 0.22f, 1f))
        val buttonOver = rect(Color(0.14f, 0.20f, 0.30f, 1f))
        val buttonChecked = rect(Color(0.06f, 0.35f, 0.45f, 1f))
        val sliderBg = rect(Color(0.20f, 0.26f, 0.36f, 1f), height = 6)
        val sliderFill = rect(ACCENT, height = 6)
        val knob = circle(ACCENT, 26)
        val boxOff = box(Color(0.20f, 0.26f, 0.36f, 1f), filled = false)
        val boxOn = box(ACCENT, filled = true)
        skin.add("panel", panel, Drawable::class.java)

        skin.add("default", Label.LabelStyle(font, TEXT))
        skin.add("muted", Label.LabelStyle(font, MUTED))
        skin.add("accent", Label.LabelStyle(font, ACCENT))
        skin.add("default", TextButton.TextButtonStyle(button, buttonChecked, buttonChecked, font).apply {
            over = buttonOver
            fontColor = TEXT
            checkedFontColor = Color.WHITE
        })
        skin.add("default-horizontal", Slider.SliderStyle(sliderBg, knob).apply { knobBefore = sliderFill })
        skin.add("default", CheckBox.CheckBoxStyle(boxOff, boxOn, font, TEXT))
        skin.add("default", ScrollPane.ScrollPaneStyle())
        return skin
    }

    private fun rect(color: Color, width: Int = 8, height: Int = 8): Drawable {
        val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888).apply { setColor(color); fill() }
        return TextureRegionDrawable(Texture(pixmap)).also { pixmap.dispose() }
    }

    private fun circle(color: Color, size: Int): Drawable {
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888).apply {
            setColor(0f, 0f, 0f, 0f); fill()
            setColor(color); fillCircle(size / 2, size / 2, size / 2 - 1)
        }
        return TextureRegionDrawable(Texture(pixmap).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) })
            .also { pixmap.dispose() }
    }

    private fun box(color: Color, filled: Boolean): Drawable {
        val size = 30
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888).apply {
            setColor(0f, 0f, 0f, 0f); fill()
            setColor(color)
            if (filled) fillRectangle(0, 0, size, size) else for (k in 0 until 3) drawRectangle(k, k, size - 2 * k, size - 2 * k)
        }
        return TextureRegionDrawable(Texture(pixmap)).also { pixmap.dispose() }
    }
}
