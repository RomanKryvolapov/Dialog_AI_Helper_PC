/**
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
package models

/**
 * Please follow code style when editing project
 * Please follow principles of clean architecture
 * Created & Copyright 2025 by Roman Kryvolapov
 **/
import models.common.TypeEnum
import java.util.Locale

enum class ApplicationLanguage(
    override val type: String,
    val nameString: String,
    val englishNameString: String,
) : TypeEnum {
    AFRIKAANS("af", "Afrikaans", "Afrikaans"),
    AMHARIC("am", "አማርኛ", "Amharic"),
    ARABIC("ar", "العربية", "Arabic"),
    BULGARIAN("bg", "Български", "Bulgarian"),
    CATALAN("ca", "Català", "Catalan"),
    CHINESE_SIMPLIFIED("zh", "简体中文", "Chinese (Simplified)"),
    CROATIAN("hr", "Hrvatski", "Croatian"),
    CZECH("cs", "Čeština", "Czech"),
    DANISH("da", "Dansk", "Danish"),
    DUTCH("nl", "Nederlands", "Dutch"),
    ENGLISH("en", "English", "English"),
    ESTONIAN("et", "Eesti", "Estonian"),
    FILIPINO("fil", "Filipino", "Filipino"),
    FINNISH("fi", "Suomi", "Finnish"),
    FRENCH("fr", "Français", "French"),
    GERMAN("de", "Deutsch", "German"),
    GREEK("el", "Ελληνικά", "Greek"),
    HINDI("hi", "हिन्दी", "Hindi"),
    HUNGARIAN("hu", "Magyar", "Hungarian"),
    ICELANDIC("is", "Íslenska", "Icelandic"),
    INDONESIAN("in", "Bahasa Indonesia", "Indonesian"),
    ITALIAN("it", "Italiano", "Italian"),
    JAPANESE("ja", "日本語", "Japanese"),
    KOREAN("ko", "한국어", "Korean"),
    LATVIAN("lv", "Latviešu", "Latvian"),
    LITHUANIAN("lt", "Lietuvių", "Lithuanian"),
    MALAY("ms", "Bahasa Melayu", "Malay"),
    NORWEGIAN("no", "Norsk", "Norwegian"),
    POLISH("pl", "Polski", "Polish"),
    PORTUGUESE_PORTUGAL("pt", "Português", "Portuguese"),
    ROMANIAN("ro", "Română", "Romanian"),
    RUSSIAN("ru", "Русский", "Russian"),
    SERBIAN("sr", "Српски", "Serbian"),
    SLOVAK("sk", "Slovenčina", "Slovak"),
    SLOVENIAN("sl", "Slovenščina", "Slovenian"),
    SPANISH_SPAIN("es", "Español", "Spanish"),
    SWAHILI("sw", "Kiswahili", "Swahili"),
    SWEDISH("sv", "Svenska", "Swedish"),
    THAI("th", "ไทย", "Thai"),
    TURKISH("tr", "Türkçe", "Turkish"),
    UKRAINIAN("uk", "Українська", "Ukrainian"),
    VIETNAMESE("vi", "Tiếng Việt", "Vietnamese"),
    ZULU("zu", "isiZulu", "Zulu");
    fun getLocale(): Locale =
        if (type.isBlank()) Locale.getDefault()
        else Locale.forLanguageTag(type)
}