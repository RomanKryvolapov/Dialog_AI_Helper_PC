package utils

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference

/**
 * The Windows calls JavaFX has no API for. Each JavaFX window is found by its title among this process's windows.
 * Call on the FX thread: the window that hides others from the taskbar is made there, so the FX thread's message
 * loop serves it.
 */
object WindowsDesktop {

    private const val GWLP_HWNDPARENT = -8
    private const val SW_HIDE = 0
    private const val SW_SHOWNA = 8

    val supported: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /**
     * Owns the windows kept off the taskbar. Windows gives a taskbar button and an Alt+Tab entry to a window
     * without an owner; one owned by a hidden window gets neither, and keeps its own title bar and buttons
     * (a tool window, the other way, loses minimise and maximise).
     */
    private val hiddenOwner: HWND by lazy {
        User32.INSTANCE.CreateWindowEx(0, "STATIC", "", 0, 0, 0, 0, 0, null, null, null, null)
    }

    /** This process's top-level window with [title], or null. */
    fun findWindow(title: String): HWND? {
        if (!supported) return null
        val pid = ProcessHandle.current().pid().toInt()
        var found: HWND? = null
        User32.INSTANCE.EnumWindows({ hwnd, _ ->
            val owner = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, owner)
            if (owner.value == pid) {
                val buffer = CharArray(512)
                val length = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.size)
                if (String(buffer, 0, length) == title) found = hwnd
            }
            found == null
        }, null)
        return found
    }

    /**
     * Takes [window] off the taskbar and out of Alt+Tab, or puts it back. A shown window is hidden and shown again
     * for the taskbar to notice, without taking the focus.
     */
    fun setOffTaskbar(window: HWND, off: Boolean) {
        val user32 = User32.INSTANCE
        val visible = user32.IsWindowVisible(window)
        if (visible) user32.ShowWindow(window, SW_HIDE)
        user32.SetWindowLongPtr(window, GWLP_HWNDPARENT, if (off) hiddenOwner.pointer else null)
        if (visible) user32.ShowWindow(window, SW_SHOWNA)
    }
}
