package utils

/**
 * Phrases Whisper writes down where nobody spoke — on silence, noise, breathing or music — learned from the
 * video subtitles it was trained on: "Thank you.", "Thanks for watching!", a lone "you", a subtitler's credit.
 * A study of Whisper on non-speech audio (Barański et al., 2025) found them in 40% of its runs, "thank you" and
 * "thanks for watching" alone making a third of those. Each is said in earnest sometimes; an utterance made of
 * nothing else is still far more likely one of these, so it is dropped as silence.
 */
object WhisperHallucinations {

    // Declared before the phrase sets, which normalize() builds on initialisation.
    /** Between sentences: a space after their end mark (so "Amara.org" or "А.Синецкая" stays whole). */
    private val SENTENCE_BREAK = Regex("""(?<=[.!?…])\s+""")
    private val NOT_A_WORD = Regex("""[\p{P}\p{S}]+""")
    private val SPACES = Regex("""\s+""")

    /**
     * Lines of video subtitles that nobody says on a call: cut out wherever they stand, since Whisper also appends
     * them to real speech (seen: "…beautifully. Thank you for watching. Please subscribe to my channel.").
     * Compared without case, punctuation or symbols (see [normalize]).
     */
    private val VIDEO_PHRASES: Set<String> = listOf(
        "thanks for watching",
        "thank you for watching",
        "thank you for watching please subscribe",
        "please subscribe",
        "please subscribe to my channel",
        "subscribe to my channel",
        "like and subscribe",
        "don't forget to like and subscribe",
        "see you in the next video",
        "subtitles by the amara.org community",
        "transcription by castingwords",
        "продолжение следует",
        "спасибо за просмотр",
        "подписывайтесь на канал",
        "субтитры сделал dimatorzok",
        "субтитры создавал dimatorzok",
        "редактор субтитров а.синецкая корректор а.егорова",
    ).map(::normalize).toSet()

    /** Said for real too, so they drop an utterance only when it holds nothing else. */
    private val SILENCE_PHRASES: Set<String> = listOf(
        "thank you",
        "thank you very much",
        "thank you so much",
        "thanks for listening",
        "thank you for listening",
        "see you next time",
        "you",
        "music",
        "applause",
        "silence",
        "blank audio",
        "до новых встреч",
    ).map(::normalize).toSet()

    /** A run of this many identical sentences or more is a loop, not speech: it is kept once. */
    private const val LOOP_REPEATS = 3

    /**
     * What is left of [text] as speech: null when it is nothing but such phrases or holds no word at all ("♪♪");
     * otherwise the text without video-subtitle lines and with any sentence looped [LOOP_REPEATS] or more times in
     * a row kept once, and else exactly as it came.
     */
    fun clean(text: String): String? {
        val sentences = text.trim().split(SENTENCE_BREAK).filter { normalize(it).isNotEmpty() }
        if (sentences.isEmpty()) return null
        val kept = ArrayList<String>()
        var i = 0
        while (i < sentences.size) {
            var run = 1
            while (i + run < sentences.size && normalize(sentences[i + run]) == normalize(sentences[i])) run += 1
            if (run >= LOOP_REPEATS) kept.add(sentences[i]) else repeat(run) { kept.add(sentences[i + it]) }
            i += run
        }
        val speech = kept.filter { normalize(it) !in VIDEO_PHRASES }
        if (speech.all { normalize(it) in SILENCE_PHRASES }) return null
        return if (speech.size == sentences.size) text else speech.joinToString(" ")
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(NOT_A_WORD, " ").replace(SPACES, " ").trim()
}
