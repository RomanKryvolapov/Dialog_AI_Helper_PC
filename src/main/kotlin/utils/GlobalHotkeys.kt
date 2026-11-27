package utils

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A key combination Windows can register for the whole desktop: at least one modifier and one key. */
data class Hotkey(val modifiers: Int, val keyCode: Int, val text: String) {

    companion object {
        const val MOD_ALT = 0x1
        const val MOD_CONTROL = 0x2
        const val MOD_SHIFT = 0x4
        const val MOD_WIN = 0x8

        private val MODIFIER_NAMES = listOf(MOD_CONTROL to "Ctrl", MOD_ALT to "Alt", MOD_SHIFT to "Shift", MOD_WIN to "Win")

        /**
         * "Ctrl+Alt+H", "shift + win + f5", "Ctrl+Space": modifiers (Ctrl, Alt, Shift, Win) in any order and case and
         * one key (a letter, a digit, F1–F24, Space). Null for anything else, a blank text and a key without a
         * modifier among them: a plain key taken for the whole desktop could no longer be typed anywhere.
         */
        fun parse(text: String): Hotkey? {
            var modifiers = 0
            var key: Pair<Int, String>? = null
            for (part in text.split('+').map { it.trim() }) {
                val modifier = when (part.lowercase()) {
                    "ctrl", "control" -> MOD_CONTROL
                    "alt" -> MOD_ALT
                    "shift" -> MOD_SHIFT
                    "win", "windows" -> MOD_WIN
                    else -> null
                }
                if (modifier != null) {
                    modifiers = modifiers or modifier
                } else {
                    if (key != null) return null
                    key = keyOf(part) ?: return null
                }
            }
            val (code, name) = key ?: return null
            if (modifiers == 0) return null
            val prefix = MODIFIER_NAMES.filter { (flag, _) -> modifiers and flag != 0 }.joinToString("") { "${it.second}+" }
            return Hotkey(modifiers, code, prefix + name)
        }

        /** The Windows virtual-key code and the name it is written with. */
        private fun keyOf(part: String): Pair<Int, String>? {
            val upper = part.uppercase()
            return when {
                upper.length == 1 && upper[0] in 'A'..'Z' -> upper[0].code to upper
                upper.length == 1 && upper[0] in '0'..'9' -> upper[0].code to upper
                upper == "SPACE" -> 0x20 to "Space"
                upper.startsWith("F") && upper.drop(1).toIntOrNull() in 1..24 -> (0x70 + upper.drop(1).toInt() - 1) to upper
                else -> null
            }
        }
    }
}

/**
 * Calls [onPressed] with the action whose [Hotkey] was pressed, whatever program has the focus. Windows delivers a
 * hotkey to the thread that registered it, so each set of them runs on a thread of its own that waits for them.
 */
class GlobalHotkeys<A : Any>(private val onPressed: (A) -> Unit) {

    private companion object {
        const val MOD_NOREPEAT = 0x4000
        const val REGISTER_WAIT_SECONDS = 2L
    }

    private val log = LoggerFactory.getLogger("GlobalHotkeys")
    private var thread: Thread? = null
    @Volatile
    private var threadId = 0

    private val problemsFlow = MutableStateFlow<Map<A, String>>(emptyMap())
    /** The actions whose combination could not be registered (another program holds it), and why. */
    val problems: StateFlow<Map<A, String>> = problemsFlow

    /** Registers [bindings] in place of the ones before. */
    @Synchronized
    fun register(bindings: Map<A, Hotkey>) {
        stop()
        problemsFlow.value = emptyMap()
        if (bindings.isEmpty()) return
        val actions = bindings.keys.toList()
        val registered = CountDownLatch(1)
        thread = Thread({
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val problems = HashMap<A, String>()
            val ids = actions.indices.filter { id ->
                val hotkey = bindings.getValue(actions[id])
                User32.INSTANCE.RegisterHotKey(null, id + 1, hotkey.modifiers or MOD_NOREPEAT, hotkey.keyCode).also { ok ->
                    if (!ok) {
                        problems[actions[id]] = "${hotkey.text} is taken by another program"
                        log.warn("Could not register ${hotkey.text}: error ${Kernel32.INSTANCE.GetLastError()}")
                    }
                }
            }
            problemsFlow.value = problems
            registered.countDown()
            if (ids.isEmpty()) return@Thread
            val message = WinUser.MSG()
            while (User32.INSTANCE.GetMessage(message, null, 0, 0) > 0) {
                if (message.message == WinUser.WM_HOTKEY) actions.getOrNull(message.wParam.toInt() - 1)?.let(onPressed)
            }
            ids.forEach { User32.INSTANCE.UnregisterHotKey(null, it + 1) }
        }, "global-hotkeys").apply {
            isDaemon = true
            start()
        }
        registered.await(REGISTER_WAIT_SECONDS, TimeUnit.SECONDS)
    }

    /** Frees the combinations: the waiting thread is told to quit and unregisters them on its way out. */
    @Synchronized
    fun stop() {
        val running = thread ?: return
        User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
        running.join(1_000)
        thread = null
    }
}
