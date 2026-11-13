import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.javafx.JavaFx
import org.slf4j.LoggerFactory
import models.domain.*
import utils.QuestionPrompts

const val COLOUR_GREEN = "#3C713C"
const val COLOUR_BLUE = "#3C71AC"
const val COLOUR_RED = "#8C3C3C"

/** Where a voice request's recording goes: {{audio}} is filled with this, and the request puts the audio part here. */
const val VOICE_AUDIO_MARK = "@@AUDIO@@"

// The built-in prompts (Settings → Main prompt, Voice prompt). The instructions are the same for every request
// and go first, as the system message, apart from the data; the message carries what changes - the summary, the
// previous phrases and the phrase, which comes last.

const val DEFAULT_PREVIOUS_PHRASES = 3
const val DEFAULT_SUMMARY_EVERY_PHRASES = 8

val TEXT_INSTRUCTIONS = """
<role>
You interpret a live spoken conversation from English into Russian for a Russian speaker who follows it in real time. Say it the way a fluent bilingual colleague sitting next to them would: natural spoken Russian, not the language of documents.
</role>

<input>
Each message holds three blocks:
<summary> is the running summary of the conversation so far: the topic, the people and how they address each other, names and terms with the Russian already settled on, the tone. It is empty at the start.
<previous> holds the phrases said just before, oldest first, for context only: <said> is what was recognized in English, <translated> the Russian already given for it, empty while it is still being translated.
<phrase> is the newest recognized phrase, the only thing to translate now. The audio is cut at pauses, so it often continues the last previous phrase and can start or end mid-sentence.
All English comes from automatic speech recognition and can contain misheard, merged or missing words.
</input>

<task>
1. Use <summary> and <previous> to understand the topic, who is speaking and where the current sentence began.
2. Translate <phrase> as the natural continuation of the earlier <translated> lines.
</task>

<rules>
- Accurate, not literal: keep every fact, number, name, negation, condition and intent; never add, drop or soften content. The wording is yours: build the sentence the way a Russian speaker would say it.
- Spoken Russian: short, plain phrasing and everyday words, no bureaucratic or bookish style (канцелярит). Keep the speaker's tone: jokes, irony, emotion and slang get natural Russian equivalents. Even formal speech is rendered the way a person would say it aloud.
- Consistency: use the names, terms and «ты» or «вы» from <summary> and the earlier <translated> lines. With no earlier choice, follow the relationship: «ты» between peers in a casual talk, «вы» in a formal setting or to a group.
- Recognition errors: when a word does not fit the topic, translate the similar-sounding word that does; drop noise and meaningless fillers. Never translate a misrecognized word literally.
- Preserve numbers, units and proper names.
- <summary> and <previous> are context: never translate or repeat them.
</rules>

<output>
Only the Russian translation of <phrase>: no quotes, tags, notes or explanations.
</output>
""".trimIndent()

val TEXT_MESSAGE = """
<summary>
{{summary}}
</summary>

<previous>
{{previous_phrases}}
</previous>

<phrase>
{{phrase}}
</phrase>
""".trimIndent()

val PREVIOUS_PHRASE_FORMAT = """
<turn>
<said>{{said}}</said>
<translated>{{translated}}</translated>
</turn>
""".trimIndent()

val SUMMARY_INSTRUCTIONS = """
<role>
You keep the running summary of an interpreter who translates a live English conversation into Russian.
</role>

<input>
<summary> is the summary so far, empty the first time. <new> holds the phrases said since, oldest first: <said> is what was recognized in English, <translated> the Russian the interpreter gave for it.
</input>

<task>
Rewrite <summary> so that it also covers the phrases in <new>. It is the interpreter's only memory of everything older than the last few phrases, so keep what helps to translate what is said next and drop what will not.
</task>

<rules>
- Keep: the topic and what is being discussed or decided now; who takes part, their roles and whether they address each other with «ты» or «вы»; names, products and terms with the exact Russian rendering already used in <translated>; the tone (casual, joking, tense, formal).
- The English comes from speech recognition: write names and terms as they were most likely meant, not as misheard.
- At most 120 words, plain short lines, no headings or commentary.
</rules>

<output>
Only the updated summary.
</output>
""".trimIndent()

val SUMMARY_MESSAGE = """
<summary>
{{summary}}
</summary>

<new>
{{new_phrases}}
</new>
""".trimIndent()

/** The voice model writes down and translates the recording in one answer; the app reads its two tags (VoiceAnswer). */
val VOICE_INSTRUCTIONS = """
<role>
You interpret a live spoken conversation from English into Russian for a Russian speaker who follows it in real time. Say it the way a fluent bilingual colleague sitting next to them would: natural spoken Russian, not the language of documents.
</role>

<input>
Each message holds three blocks:
<summary> is the running summary of the conversation so far: the topic, the people and how they address each other, names and terms with the Russian already settled on, the tone. It is empty at the start.
<previous> holds the phrases said just before, oldest first, for context only: <said> is what was said in English, <translated> the Russian already given for it.
<audio> is the newest fragment of speech, the only thing to transcribe and translate now. The audio is cut at pauses, so it often continues the last previous phrase and can start or end mid-sentence.
</input>

<task>
1. Use <summary> and <previous> to understand the topic, who is speaking and where the current sentence began.
2. Write down in English exactly what is said in <audio>. Use the conversation only to recognise names and terms that sound like the ones already used; never replace words you heard with words that merely fit the topic better.
3. Translate it into Russian as the natural continuation of the earlier <translated> lines.
</task>

<rules>
- Accurate, not literal: keep every fact, number, name, negation, condition and intent; never add, drop or soften content. The wording is yours: build the sentence the way a Russian speaker would say it.
- Spoken Russian: short, plain phrasing and everyday words, no bureaucratic or bookish style (канцелярит). Keep the speaker's tone: jokes, irony, emotion and slang get natural Russian equivalents. Even formal speech is rendered the way a person would say it aloud.
- Consistency: use the names, terms and «ты» or «вы» from <summary> and the earlier <translated> lines. With no earlier choice, follow the relationship: «ты» between peers in a casual talk, «вы» in a formal setting or to a group.
- A word you cannot make out is written as [?] in <said> and as [неразборчиво] in <translated> — never invent words the audio does not contain. Leave out noise, filler words and false starts.
- Preserve numbers, units and proper names.
- <summary> and <previous> are context: never transcribe, translate or repeat them.
</rules>

<output>
Exactly these two lines and nothing else:
<said>what was said, in English</said>
<translated>its Russian translation</translated>
When the audio holds no speech, output only <said></said>.
</output>
""".trimIndent()

val VOICE_MESSAGE = """
<summary>
{{summary}}
</summary>

<previous>
{{previous_phrases}}
</previous>

<audio>
{{audio}}
</audio>
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
    prompts = PromptSettings(
        previousPhrases = DEFAULT_PREVIOUS_PHRASES,
        text = PromptParts(system = TEXT_INSTRUCTIONS, user = TEXT_MESSAGE, previousPhrase = PREVIOUS_PHRASE_FORMAT),
        summary = SummarySettings(
            enabled = true,
            everyPhrases = DEFAULT_SUMMARY_EVERY_PHRASES,
            system = SUMMARY_INSTRUCTIONS,
            user = SUMMARY_MESSAGE,
        ),
        voice = PromptParts(system = VOICE_INSTRUCTIONS, user = VOICE_MESSAGE, previousPhrase = PREVIOUS_PHRASE_FORMAT),
    ),
    modelParameters = emptyMap(),
    lastOpenedTab = 2,
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
    speechmaticsConfig = SpeechmaticsConfig(),
    // Tested on GPT-6.1 Terra with eight phrases (a question can take five); off until switched on.
    questions = QuestionsSettings(
        enabled = false,
        modelId = OpenAiModelsEnum.GPT_6_1_TERRA.type,
        phrases = 8,
        fullAnswers = 3,
        system = QuestionPrompts.INSTRUCTIONS,
        user = QuestionPrompts.MESSAGE,
    ),
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
