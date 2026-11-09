package models.domain

/**
 * One way to turn a phrase into its translation, chosen on two levels.
 *
 * The first level is how the speech is heard: by a model that hears audio itself ([recognizer] null — a
 * voice method: the recorded phrase goes to [model], which writes both what was said and the translation),
 * or by a speech [recognizer]. The second level only exists for a recogniser: the [model] that translates
 * its text.
 *
 * Settings → General holds a chain of up to three, tried in order when the one before fails.
 */
data class TranslationMethod(val model: LlmModel, val recognizer: VoiceRecognitionEngine?) {

    val voice: Boolean get() = recognizer == null

    /** How the method is saved and named in `.env`: "voice:<model id>" or "<recogniser slug>:<model id>". */
    val key: String get() = (recognizer?.slug ?: VOICE_PREFIX) + ":" + model.id

    val title: String get() = if (recognizer == null) "${model.title} (hears the audio)" else "${recognizer.title} → ${model.title}"

    val shortTitle: String get() = if (recognizer == null) "${model.shortTitle} voice" else "${model.shortTitle} text"

    companion object {
        const val VOICE_PREFIX = "voice"
        /** The prefix of the first chains: a text method on the recogniser that was then a separate setting. */
        const val LEGACY_TEXT_PREFIX = "text"

        /** Method 1 plus up to two reserves. */
        const val MAX_CHAIN = 3
    }
}

/** The first level of a method: a model that hears audio itself, or a speech recogniser. */
sealed interface Hearing {
    val title: String

    data class Voice(val model: LlmModel) : Hearing {
        override val title: String get() = "Voice: ${model.title} hears the audio"
    }

    data class Recognition(val recognizer: VoiceRecognitionEngine) : Hearing {
        override val title: String get() = "Recognition: ${recognizer.title}"
    }
}

val TranslationMethod.hearing: Hearing
    get() = recognizer?.let { Hearing.Recognition(it) } ?: Hearing.Voice(model)

/** Every first-level choice: the models that hear audio, then the recognisers on offer. */
fun allHearings(): List<Hearing> =
    allLlmModels().filter { it.hearsAudio }.map { Hearing.Voice(it) } +
        VoiceRecognitionEngine.entries.filter { it.offered }.map { Hearing.Recognition(it) }

/** The second level: models that translate a recogniser's text. */
fun textModels(): List<LlmModel> = allLlmModels().filter { it.readsText }

/**
 * The first-level choices that can follow method 1 ([primary]) as a reserve. A phrase recorded at pauses —
 * by a voice method 1 or Whisper (batch) — can go to a voice model, or be transcribed by Whisper for a text
 * model. A streaming recogniser keeps no recording, so its reserves can only translate the same text with
 * another model.
 */
fun reserveHearings(primary: TranslationMethod): List<Hearing> {
    val recognizer = primary.recognizer
    if (recognizer != null && !recognizer.recordsPhrases) return listOf(Hearing.Recognition(recognizer))
    return allLlmModels().filter { it.hearsAudio }.map { Hearing.Voice(it) } +
        Hearing.Recognition(VoiceRecognitionEngine.WHISPER_BATCH)
}

/** Whether [method] can follow [primary] in a chain (see [reserveHearings]). */
fun fitsAfter(primary: TranslationMethod, method: TranslationMethod): Boolean =
    method.hearing in reserveHearings(primary)

/**
 * The method a saved [key] names, following a dropped model to its successor. A key of the first chains
 * ("text:<id>", or a bare model id) is a text method on [legacyRecognizer]; a recogniser not on offer
 * becomes Whisper (batch). Null when the model is unknown or cannot work that way.
 */
fun resolveTranslationMethod(key: String, legacyRecognizer: VoiceRecognitionEngine? = null): TranslationMethod? {
    val trimmed = key.trim()
    val colon = trimmed.indexOf(':')
    val prefix = if (colon < 0) TranslationMethod.LEGACY_TEXT_PREFIX else trimmed.substring(0, colon)
    val model = resolveLlmModel(trimmed.substring(colon + 1)) ?: return null
    if (prefix == TranslationMethod.VOICE_PREFIX) return TranslationMethod(model, null).takeIf { model.hearsAudio }
    val recognizer = when (prefix) {
        TranslationMethod.LEGACY_TEXT_PREFIX -> legacyRecognizer ?: VoiceRecognitionEngine.WHISPER_BATCH
        else -> VoiceRecognitionEngine.bySlug(prefix) ?: return null
    }.takeIf { it.offered } ?: VoiceRecognitionEngine.WHISPER_BATCH
    return TranslationMethod(model, recognizer).takeIf { model.readsText }
}

/**
 * [chain] with its text methods on [recognizer] — the built-in default chain for someone who, before
 * methods existed, chose only a recogniser (saved, or in `.env`) and kept the default model. A recogniser
 * not on offer leaves the chain as it is.
 */
fun onRecognizer(chain: List<TranslationMethod>, recognizer: VoiceRecognitionEngine?): List<TranslationMethod> =
    if (recognizer == null || !recognizer.offered) chain
    else chain.map { if (it.voice) it else it.copy(recognizer = recognizer) }

/**
 * A saved chain as the app runs it: known methods only, each once, at most [TranslationMethod.MAX_CHAIN],
 * and only reserves that can follow method 1.
 */
fun resolveMethodChain(keys: List<String>, legacyRecognizer: VoiceRecognitionEngine? = null): List<TranslationMethod> {
    val methods = keys.mapNotNull { resolveTranslationMethod(it, legacyRecognizer) }.distinctBy { it.key }
    val primary = methods.firstOrNull() ?: return emptyList()
    return (listOf(primary) + methods.drop(1).filter { fitsAfter(primary, it) }).take(TranslationMethod.MAX_CHAIN)
}
