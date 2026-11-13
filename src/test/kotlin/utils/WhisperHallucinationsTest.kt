package utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhisperHallucinationsTest {

    @Test
    fun `an utterance made only of what Whisper writes on silence is dropped`() {
        listOf(
            " Thank you.",
            "Thanks for watching!",
            "you",
            "Thank you. Thank you. Thank you.",
            "Thank you for watching. Please subscribe to my channel.",
            "[Music]",
            "♪♪",
            "Subtitles by the Amara.org community",
            "Продолжение следует...",
            "Редактор субтитров А.Синецкая Корректор А.Егорова",
        ).forEach { assertNull(WhisperHallucinations.clean(it), "kept: $it") }
    }

    @Test
    fun `speech is left exactly as it came, even when it holds one of those phrases`() {
        listOf(
            " Thank you for the update, Mike.",
            "Thank you. So the release moves to Tuesday.",
            "you know what I mean",
            "Okay.",
            "No. No.",
            "We can see the music video next week.",
        ).forEach { assertEquals(it, WhisperHallucinations.clean(it)) }
    }

    @Test
    fun `video subtitle lines are cut out of real speech, a real thank you is kept beside it`() {
        assertEquals(
            "beautifully. See you next time.",
            WhisperHallucinations.clean("beautifully. Thank you for watching. Please subscribe to my channel. See you next time."),
        )
        assertEquals("Great, thank you. Let's move on.", WhisperHallucinations.clean("Great, thank you. Let's move on."))
    }

    @Test
    fun `a sentence looped three times or more is kept once`() {
        assertEquals(
            "I'm going to go. And then we ship.",
            WhisperHallucinations.clean("I'm going to go. I'm going to go. I'm going to go. And then we ship."),
        )
        assertEquals("So we wait.", WhisperHallucinations.clean("So we wait. so we wait! So we wait. So we wait."))
    }
}
