import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import models.ApplicationInfo
import models.ApplicationInfoNullable
import models.ApplicationLanguage
import models.TranslateWithGoogleAiModelsEnum
import java.util.prefs.Preferences

const val COLOUR_GREEN = "#3C713C"
const val COLOUR_BLUE = "#3C71AC"
const val COLOUR_RED = "#8C3C3C"

const val TRANSLATE_FROM_LANGUAGE = "TRANSLATE_FROM_LANGUAGE"
const val TRANSLATE_TO_LANGUAGE = "TRANSLATE_TO_LANGUAGE"
const val TRANSLATE_TEXT = "TRANSLATE_TEXT"

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
    selectedModel = TranslateWithGoogleAiModelsEnum.GEMMA_3_27B,
    selectedFromLanguage = ApplicationLanguage.ENGLISH,
    selectedToLanguage = ApplicationLanguage.ENGLISH,
    googleCloudToken = "",
    prompt = PROMPT_FULL_SIZE,
    lastOpenedTab = 1,
    lmStudioModelName = "",
    promptsMap = mapOf(
        DEFAULT_KEY to PROMPT_FULL_SIZE
    )
)

fun buttonStyle(bgColor: String) = """
        -fx-background-color: $bgColor;
    """.trimIndent()

val kotlinxJsonConfig: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

val client: HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(kotlinxJsonConfig)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 60_000
    }
}

val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

val applicationInfoAdapter = moshi.adapter(ApplicationInfoNullable::class.java)

val prefs: Preferences = Preferences.userRoot().node("PREFERENCES_DATABASE")
