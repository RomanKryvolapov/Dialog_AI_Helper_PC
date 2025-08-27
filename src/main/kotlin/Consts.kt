import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import models.domain.*

const val COLOUR_GREEN = "#3C713C"
const val COLOUR_BLUE = "#3C71AC"
const val COLOUR_RED = "#8C3C3C"

const val SOURCE_TEXT = "{{source_text}}"

const val SOURCE_TEXT_CLEAR = "source_text"

const val DEFAULT_KEY = "Default"

val PROMPT_FULL_SIZE = """
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

val defaultApplicationInfo = ApplicationInfo(
    lastSelectedDevice = "",
    selectedModel = LlmModel(
        id = GoogleAiModelsEnum.GEMMA_3_27B.type,
        description = GoogleAiModelsEnum.GEMMA_3_27B.getDescription(),
        engine = LlmModelEngine.GOOGLE,
        googleAiModel = GoogleAiModelsEnum.GEMMA_3_27B,
    ),
    googleCloudToken = "",
    prompt = PROMPT_FULL_SIZE,
    lastOpenedTab = 1,
    promptsMap = mapOf(
        DEFAULT_KEY to PROMPT_FULL_SIZE
    ),
    lmStudioConfig = LmStudioConfig(
        baseUrl = "http://localhost:1234",
        models = emptyList(),

    ),
    ollamaConfig = OllamaConfig(
        baseUrl = "http://localhost:11434",
        models = emptyList(),
    ),
    translateTextEverySymbols = 100,
    translateTextEveryMilliseconds = 10000L,
    voskModelPath = "",
)


val mainThreadScope = CoroutineScope(Dispatchers.JavaFx)

val backgroundThreadScope = CoroutineScope(Dispatchers.IO)

val defaultThreadScope = CoroutineScope(Dispatchers.Default)