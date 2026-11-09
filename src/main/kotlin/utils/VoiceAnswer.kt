package utils

/**
 * A voice-mode answer as it streams in: the model's transcript of the phrase and its translation,
 * each null until its tag has opened (see PROMPT_TRANSLATE_VOICE for the format).
 */
data class VoiceAnswer(val said: String?, val translated: String?) {

    companion object {
        /** Reads `<said>…</said>` and `<translated>…</translated>` from [text], tolerating unclosed tags mid-stream. */
        fun parse(text: String): VoiceAnswer = VoiceAnswer(tag(text, "said"), tag(text, "translated"))

        private fun tag(text: String, name: String): String? {
            val open = "<$name>"
            val start = text.indexOf(open)
            if (start < 0) return null
            val from = start + open.length
            val end = text.indexOf("</$name>", from)
            val body = if (end >= 0) text.substring(from, end) else text.substring(from).withoutPartialTag()
            return body.trim()
        }

        /** Mid-stream the text can end in the first characters of the next tag ("</tra"); hide them. */
        private fun String.withoutPartialTag(): String {
            val lt = lastIndexOf('<')
            return if (lt >= 0 && indexOf('>', lt) < 0) substring(0, lt) else this
        }
    }
}
