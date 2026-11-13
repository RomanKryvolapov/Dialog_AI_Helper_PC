package utils

import models.domain.resolveTranslationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveMethodTest {

    private val chain = listOf("whisper-streaming:gpt-6.1-terra", "voice:gemini-3.5-flash-lite", "sherpa-onnx:gemma-4-31b-it")
        .map { resolveTranslationMethod(it)!! }

    @Test
    fun `any failure of the method in use hands the dialog to the next one, and it stays there`() {
        val active = ActiveMethod()
        assertEquals(chain[0], active.current(chain))
        assertTrue(active.failed(chain, chain[0], "Whisper failed"))
        assertEquals(chain[1], active.current(chain))
        assertEquals(chain[1], active.current(chain), "the dialog does not drift back to method 1")
        assertTrue(active.failed(chain, chain[1], "rate limit"))
        assertEquals(chain[2], active.current(chain))
        assertFalse(active.failed(chain, chain[2], "server error"), "after the last method there is nowhere to go")
        assertEquals(chain[2], active.current(chain))
        assertEquals(setOf(chain[0].key, chain[1].key, chain[2].key), active.flow.value.left.keys)
    }

    @Test
    fun `a late failure of a method already left changes nothing`() {
        val active = ActiveMethod()
        active.failed(chain, chain[0], "first phrase failed")
        assertFalse(active.failed(chain, chain[0], "a second phrase sent before the switch failed too"))
        assertEquals(chain[1], active.current(chain))
    }

    @Test
    fun `a failure from a dialog that has ended moves nothing in the next`() {
        val active = ActiveMethod()
        val oldDialog = active.flow.value.dialog
        active.reset()
        assertFalse(active.failed(chain, chain[0], "a request sent before Stop failed after Start", oldDialog))
        assertEquals(chain[0], active.current(chain))
        assertTrue(active.failed(chain, chain[0], "this dialog's own failure"))
    }

    @Test
    fun `a new dialog starts on method 1 again`() {
        val active = ActiveMethod()
        active.failed(chain, chain[0], "x")
        active.reset()
        assertEquals(chain[0], active.current(chain))
        assertEquals(emptyMap(), active.flow.value.left)
    }
}
