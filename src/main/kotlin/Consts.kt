import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.javafx.JavaFx
import models.domain.*

const val COLOUR_GREEN = "#3C713C"
const val COLOUR_BLUE = "#3C71AC"
const val COLOUR_RED = "#8C3C3C"

const val TRANSLATE_FROM_LANGUAGE = "{{translate_from_language}}"
const val TRANSLATE_TO_LANGUAGE = "{{translate_to_language}}"
const val TRANSLATE_TEXT = "{{translate_text}}"

const val TRANSLATE_FROM_LANGUAGE_CLEAR = "translate_from_language"
const val TRANSLATE_TO_LANGUAGE_CLEAR = "translate_to_language"
const val TRANSLATE_TEXT_CLEAR = "translate_text"

const val DEFAULT_KEY = "Default"

val PROMPT_FULL_SIZE = """
Role:

You are a professional text translator.

Task:
Translate "Text to translate" from $TRANSLATE_FROM_LANGUAGE to $TRANSLATE_TO_LANGUAGE.

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
$TRANSLATE_TEXT
""".trimIndent()

val defaultApplicationInfo = ApplicationInfo(
    lastSelectedDevice = "",
    selectedModel = LlmModel(
        id = GoogleAiModelsEnum.GEMMA_3_27B.type,
        description = GoogleAiModelsEnum.GEMMA_3_27B.getDescription(),
        engine = LlmModelEngine.GOOGLE,
        googleAiModel = GoogleAiModelsEnum.GEMMA_3_27B,
    ),
    selectedFromLanguage = ApplicationLanguage.ENGLISH,
    selectedToLanguage = ApplicationLanguage.ENGLISH,
    googleCloudToken = "",
    prompt = PROMPT_FULL_SIZE,
    lastOpenedTab = 1,
    promptsMap = mapOf(
        DEFAULT_KEY to PROMPT_FULL_SIZE
    ),
    lmStudioConfig = LmStudioConfig(
        ip = "localhost",
        port = "1234",
        models = emptyList(),

    ),
    ollamaConfig = OllamaConfig(
        ip = "localhost",
        port = "11434",
        models = emptyList(),
    ),
    translateTextEverySymbols = 100,
    translateTextEveryMilliseconds = 10000L,
    voskModel = VoskModels.VOSK_MODEL_EN_US_0_22,
)


val mainThreadScope = CoroutineScope(Dispatchers.JavaFx)

val backgroundThreadScope = CoroutineScope(Dispatchers.IO)

val defaultThreadScope = CoroutineScope(Dispatchers.Default)