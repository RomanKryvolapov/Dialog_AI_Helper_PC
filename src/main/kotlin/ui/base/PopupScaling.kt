package ui.base

import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.scene.transform.Scale
import javafx.stage.PopupWindow
import javafx.stage.Screen
import javafx.stage.Window

/**
 * Drop-down lists and tooltips are windows of their own, outside [pane]: each is drawn at the size [pane] draws
 * its content at, as it opens.
 */
fun scalePopupsLike(pane: ScaledPane) {
    Window.getWindows().addListener(ListChangeListener { change ->
        while (change.next()) {
            change.addedSubList.filterIsInstance<PopupWindow>().forEach { scale(it, pane.drawnScale) }
        }
    })
}

/**
 * The control that opens [popup] places it for its unscaled height, and may place it again while it is open: a
 * popup drawn larger can then reach below the screen, so each time it does it is moved up into it.
 */
private fun scale(popup: PopupWindow, size: Double) {
    val contents = popup.scene?.root?.childrenUnmodifiable ?: return
    contents.forEach { content ->
        content.transforms.removeIf { it is Scale }
        content.transforms.add(Scale(size, size, 0.0, 0.0))
    }
    if (size <= 1.0) return

    // A popup stays where its anchor says, and its window follows a moment later: the anchor is what is read and moved.
    val anchorBelowTop = (popup.anchorY - popup.y).takeUnless { it.isNaN() } ?: 0.0
    val keepOnScreen = {
        Screen.getScreensForRectangle(popup.x, popup.anchorY, 1.0, 1.0).firstOrNull()?.visualBounds?.let { screen ->
            val lowest = maxOf(screen.minY, screen.maxY - popup.height) + anchorBelowTop
            if (popup.anchorY > lowest + 0.5) popup.anchorY = lowest
        }
    }
    val onMoved = ChangeListener<Number> { _, _, _ -> keepOnScreen() }
    popup.anchorYProperty().addListener(onMoved)
    popup.heightProperty().addListener(onMoved)
    popup.showingProperty().addListener(object : ChangeListener<Boolean> {
        override fun changed(observable: ObservableValue<out Boolean>, was: Boolean, showing: Boolean) {
            if (showing) return
            popup.anchorYProperty().removeListener(onMoved)
            popup.heightProperty().removeListener(onMoved)
            popup.showingProperty().removeListener(this)
        }
    })
    keepOnScreen()
}
