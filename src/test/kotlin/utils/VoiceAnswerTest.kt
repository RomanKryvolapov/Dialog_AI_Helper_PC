package utils

import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceAnswerTest {

    @Test
    fun `a complete answer gives both parts`() {
        val answer = VoiceAnswer.parse(
            "<said>Honestly, I haven't.</said>\n<translated>Честно говоря, не спал.</translated>"
        )
        assertEquals(VoiceAnswer("Honestly, I haven't.", "Честно говоря, не спал."), answer)
    }

    @Test
    fun `mid-stream the open tag shows what has arrived so far`() {
        assertEquals(VoiceAnswer(null, null), VoiceAnswer.parse(""))
        assertEquals(VoiceAnswer(null, null), VoiceAnswer.parse("<sai"))
        assertEquals(VoiceAnswer("Honestly, I", null), VoiceAnswer.parse("<said>Honestly, I"))
        assertEquals(VoiceAnswer("Honestly, I haven't.", null), VoiceAnswer.parse("<said>Honestly, I haven't.</sa"))
        assertEquals(
            VoiceAnswer("Honestly, I haven't.", "Честно"),
            VoiceAnswer.parse("<said>Honestly, I haven't.</said>\n<translated>Честно</tr"),
        )
    }

    @Test
    fun `no speech is an empty transcript, not a missing one`() {
        assertEquals(VoiceAnswer("", null), VoiceAnswer.parse("<said></said>"))
    }

    @Test
    fun `an answer without the tags parses to nothing`() {
        assertEquals(VoiceAnswer(null, null), VoiceAnswer.parse("Честно говоря, не спал."))
    }

    @Test
    fun `a comparison sign inside the text is kept once the tag is closed`() {
        assertEquals(
            VoiceAnswer("latency < 2 s", "задержка < 2 с"),
            VoiceAnswer.parse("<said>latency < 2 s</said><translated>задержка < 2 с</translated>"),
        )
    }
}
