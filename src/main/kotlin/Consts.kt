import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import models.domain.*

const val COLOUR_GREEN = "#3C713C"
const val COLOUR_BLUE = "#3C71AC"
const val COLOUR_RED = "#8C3C3C"

const val SOURCE_TEXT = "{{source_text}}"

const val SOURCE_TEXT_CLEAR = "source_text"

val PROMPT_TRANSLATE_TEXT = """
Role:

You are a professional text translator.

Task:
Translate "Text to translate" from English to Russian.

Rules:
The text is a result of voice recognition, so some words may not be recognized correctly.
Try to understand what exactly was meant.
If a word is not related to the context, try to find a more appropriate word, or a word that is related to the context and sounds similar in the source language, or ignore this word.
Do not try to translate literally, as this is a dialogue and some phrases may not have meaning, translate the essence of what was said.
Don't write an explanation, just a translation.

Output:
- Translated text only

Input:
- "Text to translate":
$SOURCE_TEXT
""".trimIndent()

val PROMPT_ANSWER_TO_QUESTIONS = """
    You are AI assistant
    You need to answer the question in Russian
    If you don't know the answer or the question is not a question, write about it in Russian
    Your answer should contain only a short and informative answer to the question, nothing extra, or the text I don't know the answer to the question
    Question : $SOURCE_TEXT
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
        "Answer the question" to PromptModel(
            name =  "Answer the question",
            prompt = PROMPT_ANSWER_TO_QUESTIONS,
        ),
    ),
    lmStudioConfig = LmStudioConfig(
        baseUrl = "http://localhost:1234",
        models = emptyList(),
    ),
    ollamaConfig = OllamaConfig(
        baseUrl = "http://localhost:11434",
        models = emptyList(),
    ),
    sendTextEverySymbols = 100,
    sendTextEveryMilliseconds = 10000L,
    voskModelPath = "",
    whisperModelPath = "",
    whisperModelConfig = WhisperModelConfig(
        silenceThresholdPercents = 10,
        maxSilenceMilliseconds = 500L,
        maxChunkDurationMilliseconds = 5000L,
    ),
    voiceRecognizer = VoiceRecognizerEngineEnum.VOSK,
    voiceSpeaker = VoiceSpeakerEngineEnum.NONE,
    questionLanguage = "en",
    answerLanguage = "ru",
)


val mainThreadScope = CoroutineScope(Dispatchers.JavaFx)

val backgroundThreadScope = CoroutineScope(Dispatchers.IO)

val defaultThreadScope = CoroutineScope(Dispatchers.Default)