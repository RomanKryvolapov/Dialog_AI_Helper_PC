import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.javafx.JavaFx
import org.slf4j.LoggerFactory
import models.domain.*

const val COLOUR_GREEN = "#3C713C"
const val COLOUR_BLUE = "#3C71AC"
const val COLOUR_RED = "#8C3C3C"

const val SOURCE_TEXT = "{{source_text}}"
const val SOURCE_TEXT_CLEAR = "source_text"

const val CONVERSATION_SUMMARY = "{{conversation_summary}}"
const val CONVERSATION_SUMMARY_CLEAR = "conversation_summary"
const val RECENT_TURNS = "{{recent_turns}}"
const val RECENT_TURNS_CLEAR = "recent_turns"
const val SUMMARY_TURNS_CLEAR = "turns"

const val PROMPT_NAME_TRANSLATE_CONVERSATION = "Translate text (conversation context)"

/** Where in PROMPT_TRANSLATE_VOICE the recorded phrase goes: the request puts the audio part here. */
const val VOICE_AUDIO_MARK = "@@AUDIO@@"

/**
 * The newest turns always sent verbatim, rendered: about 400–500 tokens, the last half-minute or so
 * of speech. Older turns reach the prompt only through the running summary, which is refreshed once
 * twice this much is pending — see ConversationContext.
 */
const val RECENT_TURNS_MAX_CHARS = 1500

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

val PROMPT_TRANSLATE_CONVERSATION = """
<role>
You interpret a live spoken conversation from English into Russian for a Russian speaker who follows it in real time. Say it the way a fluent bilingual colleague sitting next to them would: natural spoken Russian, not the language of documents.
</role>

<input>
<notes> are your running notes on the conversation so far: the topic, the people and how they address each other, names and terms with the Russian you settled on, the tone. They are empty at the start.
<recent> holds the latest turns, oldest first: <said> is what was recognized in English, <translated> is the Russian you already gave for it. A turn without <translated> has not been translated yet; use it for context only.
<source> is the newest recognized fragment, the only thing to translate now. The audio is cut at pauses, so <source> often continues the last turn and can start or end mid-sentence.
All English text comes from automatic speech recognition and can contain misheard, merged or missing words.
</input>

<task>
1. Use <notes> and <recent> to understand the topic, who is speaking and where the current sentence began.
2. Translate <source> as the natural continuation of your earlier <translated> lines.
</task>

<rules>
- Accurate, not literal: keep every fact, number, name, negation, condition and intent; never add, drop or soften content. The wording is yours: build the sentence the way a Russian speaker would say it.
- Spoken Russian: short, plain phrasing and everyday words, no bureaucratic or bookish style (канцелярит). Keep the speaker's tone: jokes, irony, emotion and slang get natural Russian equivalents. Even formal speech is rendered the way a person would say it aloud.
- Consistency: use the names, terms and «ты» or «вы» from <notes> and your earlier <translated> lines. With no earlier choice, follow the relationship: «ты» between peers in a casual talk, «вы» in a formal setting or to a group.
- Recognition errors: when a word does not fit the topic, translate the similar-sounding word that does; drop noise and meaningless fillers. Never translate a misrecognized word literally.
- Preserve numbers, units and proper names.
- Do not repeat or re-translate anything from <recent>.
</rules>

<output>
Only the Russian translation of <source>: no quotes, tags, notes or explanations.
</output>

<notes>
$CONVERSATION_SUMMARY
</notes>

<recent>
$RECENT_TURNS
</recent>

<source lang="en">
$SOURCE_TEXT
</source>
""".trimIndent()

/**
 * Voice mode: the phrase goes to the model as audio, so it transcribes and translates in one answer.
 * Fixed, not user-editable — the app parses the two output tags (see VoiceAnswer).
 */
val PROMPT_TRANSLATE_VOICE = """
<role>
You interpret a live spoken conversation from English into Russian for a Russian speaker who follows it in real time. Say it the way a fluent bilingual colleague sitting next to them would: natural spoken Russian, not the language of documents.
</role>

<input>
<notes> are your running notes on the conversation so far: the topic, the people and how they address each other, names and terms with the Russian you settled on, the tone. They are empty at the start.
<recent> holds the latest turns, oldest first: <said> is what was said in English, <translated> is the Russian you already gave for it.
<audio> is the newest fragment of speech, the only thing to transcribe and translate now. The audio is cut at pauses, so it often continues the last turn and can start or end mid-sentence.
</input>

<task>
1. Use <notes> and <recent> to understand the topic, who is speaking and where the current sentence began.
2. Write down in English exactly what is said in <audio>. Use the conversation only to recognise names and terms that sound like the ones already used; never replace words you heard with words that merely fit the topic better.
3. Translate it into Russian as the natural continuation of your earlier <translated> lines.
</task>

<rules>
- Accurate, not literal: keep every fact, number, name, negation, condition and intent; never add, drop or soften content. The wording is yours: build the sentence the way a Russian speaker would say it.
- Spoken Russian: short, plain phrasing and everyday words, no bureaucratic or bookish style (канцелярит). Keep the speaker's tone: jokes, irony, emotion and slang get natural Russian equivalents. Even formal speech is rendered the way a person would say it aloud.
- Consistency: use the names, terms and «ты» or «вы» from <notes> and your earlier <translated> lines. With no earlier choice, follow the relationship: «ты» between peers in a casual talk, «вы» in a formal setting or to a group.
- A word you cannot make out is written as [?] in <said> and as [неразборчиво] in <translated> — never invent words the audio does not contain. Leave out noise, filler words and false starts.
- Preserve numbers, units and proper names.
- Do not repeat or re-translate anything from <recent>.
</rules>

<output>
Exactly these two lines and nothing else:
<said>what was said, in English</said>
<translated>its Russian translation</translated>
When the audio holds no speech, output only <said></said>.
</output>

<notes>
$CONVERSATION_SUMMARY
</notes>

<recent>
$RECENT_TURNS
</recent>

<audio>
$VOICE_AUDIO_MARK
</audio>
""".trimIndent()

/** Folds the turns that left the recent window into the running notes; not user-editable. */
val PROMPT_CONVERSATION_SUMMARY = """
<role>
You keep the running notes of an interpreter who translates a live English conversation into Russian.
</role>

<task>
Rewrite <notes> so that they also cover the turns in <turns>. The notes are the interpreter's only memory of everything older than the last few turns, so keep what helps to translate what is said next and drop what will not.
</task>

<rules>
- Keep: the topic and what is being discussed or decided now; who takes part, their roles and whether they address each other with «ты» or «вы»; names, products and terms with the exact Russian rendering already used in <translated>; the tone (casual, joking, tense, formal).
- The English comes from speech recognition: write names and terms as they were most likely meant, not as misheard.
- At most 120 words, plain short lines, no headings or commentary.
</rules>

<output>
Only the updated notes.
</output>

<notes>
$CONVERSATION_SUMMARY
</notes>

<turns>
{{$SUMMARY_TURNS_CLEAR}}
</turns>
""".trimIndent()

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
    // Free tier: Gemini 3.5 Flash-Lite answers in about a second; Gemma 4 31B, with a larger daily
    // allowance, took 10–25 seconds a phrase in a November 2026 test. No reserves until the user adds them.
    translationMethods = listOf(
        TranslationMethod(
            model = LlmModel(
                id = GoogleAiModelsEnum.GEMINI_3_5_FLASH_LITE.type,
                description = GoogleAiModelsEnum.GEMINI_3_5_FLASH_LITE.getDescription(),
                engine = LlmModelEngine.GOOGLE,
                googleAiModel = GoogleAiModelsEnum.GEMINI_3_5_FLASH_LITE,
            ),
            recognizer = VoiceRecognitionEngine.WHISPER_BATCH,
        ),
    ),
    googleCloudToken = "",
    openAiToken = "",
    claudeToken = "",
    speechmaticsToken = "",
    prompt = PromptModel(
        name = PROMPT_NAME_TRANSLATE_CONVERSATION,
        prompt = PROMPT_TRANSLATE_CONVERSATION,
    ),
    lastOpenedTab = 1,
    promptsMap = mapOf(
        PROMPT_NAME_TRANSLATE_CONVERSATION to PromptModel(
            name = PROMPT_NAME_TRANSLATE_CONVERSATION,
            prompt = PROMPT_TRANSLATE_CONVERSATION,
        ),
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
    useStreamingResponse = true,
    sherpaModelPath = "",
    sherpaConfig = SherpaModelConfig(),
)


/**
 * Logs a coroutine that failed with nothing to catch it. With the [SupervisorJob]s below such a failure ends
 * only that coroutine: with a plain job it cancelled its whole scope, and with it every other piece of work
 * there — the translation scheduler, recognition, the settings listeners — until the app was restarted.
 */
private val logUncaught = CoroutineExceptionHandler { _, error ->
    LoggerFactory.getLogger("Coroutines").error("A background task failed: ${error.message}", error)
}

val mainThreadScope = CoroutineScope(SupervisorJob() + Dispatchers.JavaFx + logUncaught)

val backgroundThreadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + logUncaught)

val defaultThreadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + logUncaught)
