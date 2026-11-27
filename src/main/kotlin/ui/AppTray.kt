package ui

import org.slf4j.LoggerFactory
import java.awt.BasicStroke
import java.awt.Color
import java.awt.EventQueue
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The app's icon in the notification area, next to the clock. A click hides or shows the window; its menu also starts
 * and stops the dialog and quits. A green dot means a dialog is being listened to, an orange one an answer that came
 * while the window was hidden. The callbacks run on AWT's thread: the caller moves them to the FX thread.
 */
class AppTray private constructor(
    private val onToggleWindow: () -> Unit,
    private val onToggleDialog: () -> Unit,
    private val onExit: () -> Unit,
) {

    /** What the icon shows. */
    data class State(val windowShown: Boolean, val listening: Boolean, val unseenAnswer: Boolean)

    companion object {
        private const val TOOLTIP = "Dialog AI Translator"
        private val SIZES = listOf(16, 20, 24, 32, 40, 48, 64)
        private val LISTENING = Color(0x22, 0xC5, 0x5E)
        private val NEW_ANSWER = Color(0xF5, 0x9E, 0x0B)
        private val log = LoggerFactory.getLogger("AppTray")

        /** Null where the system has no notification area (or AWT runs headless). */
        fun create(onToggleWindow: () -> Unit, onToggleDialog: () -> Unit, onExit: () -> Unit): AppTray? {
            if (!SystemTray.isSupported()) {
                log.warn("No notification area on this system: the window cannot hide to it")
                return null
            }
            return AppTray(onToggleWindow, onToggleDialog, onExit).takeIf { it.install() }
        }

        /** The app icon drawn at [size] pixels, with the state's dots on it. */
        internal fun draw(source: BufferedImage, size: Int, state: State): BufferedImage {
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(source, 0, 0, size, size, null)
            val dot = (size * 0.42).toInt().coerceAtLeast(6)
            val ring = (size / 16f).coerceAtLeast(1f)
            fun spot(colour: Color, x: Int, y: Int) {
                g.color = colour
                g.fillOval(x, y, dot, dot)
                g.color = Color(0x10, 0x10, 0x10)
                g.stroke = BasicStroke(ring)
                g.drawOval(x, y, dot - 1, dot - 1)
            }
            if (state.listening) spot(LISTENING, size - dot, size - dot)
            if (state.unseenAnswer) spot(NEW_ANSWER, size - dot, 0)
            g.dispose()
            return image
        }
    }

    private val sources: Map<Int, BufferedImage> = listOf(16, 24, 32, 48, 64, 128, 256).associateWith { size ->
        ImageIO.read(AppTray::class.java.getResource("/icons/app-$size.png"))
    }

    private val windowItem = MenuItem("Hide window").apply { addActionListener { onToggleWindow() } }
    private val dialogItem = MenuItem("Start dialog").apply { addActionListener { onToggleDialog() } }
    private val icon = TrayIcon(iconFor(State(windowShown = true, listening = false, unseenAnswer = false)), TOOLTIP).apply {
        isImageAutoSize = true
        popupMenu = PopupMenu().apply {
            add(windowItem)
            add(dialogItem)
            addSeparator()
            add(MenuItem("Exit").apply { addActionListener { onExit() } })
        }
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && e.clickCount == 1) onToggleWindow()
            }
        })
    }

    private var shown: State? = null

    private fun install(): Boolean = try {
        SystemTray.getSystemTray().add(icon)
        true
    } catch (e: Exception) {
        log.warn("Could not add the notification-area icon: ${e.message}")
        false
    }

    /** One image per size the notification area may ask for (it follows the display scaling). */
    private fun iconFor(state: State): Image {
        val variants = SIZES.map { size ->
            val source = sources.entries.firstOrNull { it.key >= size }?.value ?: sources.getValue(256)
            draw(source, size, state)
        }
        return BaseMultiResolutionImage(*variants.toTypedArray())
    }

    fun show(state: State) {
        EventQueue.invokeLater {
            if (state == shown) return@invokeLater
            shown = state
            icon.image = iconFor(state)
            windowItem.label = if (state.windowShown) "Hide window" else "Show window"
            dialogItem.label = if (state.listening) "Stop dialog" else "Start dialog"
            icon.toolTip = TOOLTIP + if (state.listening) " — listening" else ""
        }
    }

    /** Waits until the icon is gone: one left behind by a process that quit stays until the mouse passes over it. */
    fun remove() {
        val removal = Runnable { SystemTray.getSystemTray().remove(icon) }
        if (EventQueue.isDispatchThread()) removal.run() else EventQueue.invokeAndWait(removal)
    }
}
