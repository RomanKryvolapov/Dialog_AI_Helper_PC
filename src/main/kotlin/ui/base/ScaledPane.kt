package ui.base

import javafx.scene.layout.Region
import javafx.scene.transform.Scale

/**
 * Draws [content] larger or smaller than it is laid out: at [scale] 1.5 the content gets two thirds of this
 * pane's width and height to arrange itself in, and is then drawn one and a half times as large, filling the pane.
 * So everything in it — type, gaps, fixed widths — grows or shrinks together, and a narrower window is to the
 * content simply a narrower window.
 *
 * The content is never given less than [minContentWidth] to arrange itself in: a pane narrower than that (at the
 * chosen [scale]) draws it smaller instead, as much as it takes to show that width whole.
 */
class ScaledPane(private val content: Region) : Region() {

    private val transform = Scale(1.0, 1.0, 0.0, 0.0)

    var scale: Double = 1.0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    var minContentWidth: Double = 0.0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init {
        content.transforms.add(transform)
        children.add(content)
    }

    /** The scale the content is drawn at now: [scale], or less in a pane too narrow for [minContentWidth]. */
    val drawnScale: Double get() = transform.x

    override fun layoutChildren() {
        val drawn = if (minContentWidth > 0.0 && width > 0.0) minOf(scale, width / minContentWidth) else scale
        transform.x = drawn
        transform.y = drawn
        content.resizeRelocate(0.0, 0.0, width / drawn, height / drawn)
    }
}
