import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoroutineScopesTest {

    @Test
    fun `one failed background task does not stop the others`() = runBlocking {
        backgroundThreadScope.launch { error("a task that fails with nothing to catch it") }.join()
        defaultThreadScope.launch { error("the same on the default scope") }.join()

        assertTrue(backgroundThreadScope.isActive, "the background scope was cancelled by one failure")
        assertTrue(defaultThreadScope.isActive, "the default scope was cancelled by one failure")
        val after = CompletableDeferred<String>()
        backgroundThreadScope.launch { after.complete("ran") }
        assertEquals("ran", withTimeout(5_000) { after.await() })
    }
}
