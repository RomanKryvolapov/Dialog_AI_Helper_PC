package repository

import VOICE_AUDIO_MARK
import dev.langchain4j.data.message.AudioContent
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import utils.ChatLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VoiceRequestTest {

    private val audio = AudioContent("UklGRiQAAABXQVZFZm10IBAAAAABAAEAgD4AAAB9AAACABAAZGF0YQAAAAA=", "audio/wav")

    @Test
    fun `the audio goes where the prompt marks it, inside its own block`() {
        val parts = CloudRepositoryImpl.audioMessageParts("<recent>\nturns\n</recent>\n\n<audio>\n$VOICE_AUDIO_MARK\n</audio>", audio)
        assertEquals(3, parts.size)
        assertEquals("<recent>\nturns\n</recent>\n\n<audio>\n", (parts[0] as TextContent).text())
        assertSame(audio, parts[1])
        assertEquals("\n</audio>", (parts[2] as TextContent).text())
    }

    @Test
    fun `a prompt without the mark gets the audio at the end`() {
        val parts = CloudRepositoryImpl.audioMessageParts("just text", audio)
        assertEquals(listOf("just text"), parts.filterIsInstance<TextContent>().map { it.text() })
        assertSame(audio, parts.last())
    }

    @Test
    fun `the log shows the audio's size, never its data`() {
        val logged = ChatLogger.describe(UserMessage.from(TextContent.from("prompt text"), audio))
        assertTrue("prompt text" in logged)
        assertTrue("[audio, ${audio.audio().base64Data().length} base64 chars]" in logged)
        assertFalse(audio.audio().base64Data() in logged, "the base64 audio reached the log")
    }
}
