import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import models.domain.*

const val COLOUR_GREEN = "#3C713C"
const val COLOUR_BLUE = "#3C71AC"
const val COLOUR_RED = "#8C3C3C"

const val SOURCE_TEXT = "{{source_text}}"
const val SOURCE_TEXT_CLEAR = "source_text"

/**
 * When true, the built-in prompts below overwrite the saved prompts in preferences on startup
 * (dev mode: code is the source of truth). Set to false to keep prompts edited in the Prompts tab.
 */
const val OVERWRITE_PROMPTS_FROM_CODE = true

private fun translateWithContextPrompt(previousCount: Int): String {
    val contextBlocks = (previousCount downTo 1).joinToString("\n\n") { i ->
        "<context lang=\"en\" order=\"$i-ago\">\n{{previous_text_$i}}\n</context>"
    }
    return """
<role>
You are a professional simultaneous English-to-Russian translator working on live, spoken dialogue.
</role>

<task>
<context> holds the immediately preceding fragments of the SAME ongoing speech (oldest first); <source> is the current fragment. The speech is split into chunks, so <source> is very likely a DIRECT CONTINUATION of <context> and may begin and/or end mid-sentence.
1. Read <context> and <source> together to establish the overall subject/domain and where the current sentence began.
2. Translate ONLY <source> into natural Russian, as a seamless continuation of what was already said. Do not re-translate or repeat <context>.
</task>

<rules>
- The input is from automatic speech recognition and may contain misrecognized, merged or missing words. Use the established subject and the sentence already begun in <context> to recover the intended meaning; prefer a similar-sounding word that fits the subject, or drop noise.
- <source> may start mid-phrase: translate it as the grammatical continuation of <context>, not as a standalone sentence.
- This is spoken dialogue: translate the meaning, not word for word; keep it natural and idiomatic.
- Preserve numbers, units, measurements and proper names.
</rules>

<output>
Output ONLY the Russian translation of <source> (the continuation) — no quotes, no <context>, no notes, no explanations.
</output>

$contextBlocks

<source lang="en">
$SOURCE_TEXT
</source>
""".trimIndent()
}

val PROMPT_TRANSLATE_TEXT = """
<role>
You are a professional simultaneous English-to-Russian translator working on live, spoken dialogue.
</role>

<task>
Translate the English text inside <source> into natural, fluent Russian.
</task>

<rules>
- The text comes from automatic speech recognition and may contain misrecognized, merged or missing words. Infer what was actually meant and translate that; if a word does not fit, replace it with a contextually appropriate word that sounds similar in English, or drop it.
- This is spoken dialogue: translate the meaning, not word for word; keep it natural and idiomatic.
- Preserve numbers, units, measurements and proper names.
</rules>

<output>
Output ONLY the Russian translation — no quotes, no original text, no notes, no explanations.
</output>

<source lang="en">
$SOURCE_TEXT
</source>
""".trimIndent()

val PROMPT_TRANSLATE_TEXT_1_PREV = translateWithContextPrompt(1)
val PROMPT_TRANSLATE_TEXT_2_PREV = translateWithContextPrompt(2)
val PROMPT_TRANSLATE_TEXT_3_PREV = translateWithContextPrompt(3)
val PROMPT_TRANSLATE_TEXT_4_PREV = translateWithContextPrompt(4)
val PROMPT_TRANSLATE_TEXT_5_PREV = translateWithContextPrompt(5)

val PROMPT_ANSWER_TO_QUESTIONS = """
<role>
You are a concise, knowledgeable assistant.
</role>

<task>
Answer the question inside <question>, replying in Russian.
</task>

<rules>
- Give only a short, informative answer in Russian.
- The question comes from speech recognition and may contain errors; infer the intended question.
- If <question> is not a question, or you do not know the answer, say so briefly in Russian.
</rules>

<output>
Output ONLY the answer in Russian — no preamble, no restating of the question, no explanations.
</output>

<question lang="en">
$SOURCE_TEXT
</question>
""".trimIndent()

val defaultApplicationInfo = ApplicationInfo(
    lastSelectedDevice = "",
    selectedModel = LlmModel(
        id = GoogleAiModelsEnum.GEMMA_3_27B.type,
        description = GoogleAiModelsEnum.GEMMA_3_27B.getDescription(),
        engine = LlmModelEngine.GOOGLE,
        googleAiModel = GoogleAiModelsEnum.GEMMA_3_27B,
    ),
    googleCloudToken = "",
    openAiToken = "",
    claudeToken = "",
    prompt = PromptModel(
        name = "Translate text",
        prompt = PROMPT_TRANSLATE_TEXT,
    ),
    lastOpenedTab = 1,
    promptsMap = mapOf(
        "Translate text" to PromptModel(
            name = "Translate text",
            prompt = PROMPT_TRANSLATE_TEXT,
        ),
        "Translate text (+1 previous sentence)" to PromptModel(
            name = "Translate text (+1 previous sentence)",
            prompt = PROMPT_TRANSLATE_TEXT_1_PREV,
        ),
        "Translate text (+2 previous sentences)" to PromptModel(
            name = "Translate text (+2 previous sentences)",
            prompt = PROMPT_TRANSLATE_TEXT_2_PREV,
        ),
        "Translate text (+3 previous sentences)" to PromptModel(
            name = "Translate text (+3 previous sentences)",
            prompt = PROMPT_TRANSLATE_TEXT_3_PREV,
        ),
        "Translate text (+4 previous sentences)" to PromptModel(
            name = "Translate text (+4 previous sentences)",
            prompt = PROMPT_TRANSLATE_TEXT_4_PREV,
        ),
        "Translate text (+5 previous sentences)" to PromptModel(
            name = "Translate text (+5 previous sentences)",
            prompt = PROMPT_TRANSLATE_TEXT_5_PREV,
        ),
        "Answer the question" to PromptModel(
            name = "Answer the question",
            prompt = PROMPT_ANSWER_TO_QUESTIONS,
        ),
    ),
    sendTextEverySymbols = 100,
    sendTextEveryMilliseconds = 10000L,
    whisperModelPath = "",
    whisperModelConfig = WhisperModelConfig(
        silenceThresholdPercents = 10,
        maxSilenceMilliseconds = 500L,
        maxChunkDurationMilliseconds = 5000L,
        maxConcurrentProcesses = 5,
        streamingStepMilliseconds = 700L,
        streamingMinChars = 20,
    ),
    questionLanguage = "en",
    voiceRecognitionEngine = VoiceRecognitionEngine.WHISPER_BATCH,
    useStreamingResponse = true,
    sherpaModelPath = "",
    sherpaConfig = SherpaModelConfig(),
)


val mainThreadScope = CoroutineScope(Dispatchers.JavaFx)

val backgroundThreadScope = CoroutineScope(Dispatchers.IO)

val defaultThreadScope = CoroutineScope(Dispatchers.Default)
