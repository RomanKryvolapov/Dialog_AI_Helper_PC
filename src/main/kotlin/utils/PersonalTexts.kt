package utils

import defaultApplicationInfo
import models.domain.ApplicationInfo
import models.domain.QuestionsSettings
import org.slf4j.LoggerFactory
import java.io.File

/**
 * The user's own texts, kept out of the repository in `personal/` in the working directory, the way `.env` is: facts
 * about the user, what calls are about, and any of the Questions mode's prompts. Each file name names the setting it
 * fills; they stand in for the built-in values of the settings the user has not changed ([questionsDefaults]), so a
 * value saved from Settings wins and an untouched one follows the file.
 */
object PersonalTexts {

    private class Target(val part: PromptPart?, val set: (QuestionsSettings, String) -> QuestionsSettings)

    private val TARGETS = mapOf(
        "about_user.md" to Target(null) { s, v -> s.copy(aboutUser = v) },
        "topic.txt" to Target(null) { s, v -> s.copy(topic = v) },
        "questions_instructions.md" to Target(PromptPart.QUESTIONS_INSTRUCTIONS) { s, v -> s.copy(system = v) },
        "questions_message.md" to Target(PromptPart.QUESTIONS_MESSAGE) { s, v -> s.copy(user = v) },
        "follow_ups_instructions.md" to Target(PromptPart.ANSWER_LANGUAGE_INSTRUCTIONS) { s, v -> s.copy(followUpsSystem = v) },
        "follow_ups_message.md" to Target(PromptPart.FOLLOW_UPS_MESSAGE) { s, v -> s.copy(followUpsUser = v) },
        "expansion_instructions.md" to Target(PromptPart.ANSWER_LANGUAGE_INSTRUCTIONS) { s, v -> s.copy(expansionSystem = v) },
        "expansion_message.md" to Target(PromptPart.EXPANSION_MESSAGE) { s, v -> s.copy(expansionUser = v) },
    )

    val FILE_NAMES: Set<String> = TARGETS.keys

    private const val DIR_NAME = "personal"
    private val log = LoggerFactory.getLogger("PersonalTexts")

    /** The Questions settings' fallbacks: the built-in ones with `personal/` applied, read once at first use. */
    val questionsDefaults: QuestionsSettings by lazy {
        val dir = File(File("").absolutePath, DIR_NAME)
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty().mapNotNull { file ->
            runCatching { file.name to file.readText(Charsets.UTF_8) }
                .onFailure { log.warn("{} could not be read: {}", file.name, it.message) }.getOrNull()
        }.toMap()
        if (files.isEmpty()) return@lazy defaultApplicationInfo.questions
        val result = apply(files, defaultApplicationInfo)
        log.info("Personal texts from {}: {}", dir.absolutePath, result.applied.ifEmpty { listOf("none") }.joinToString())
        result.skipped.forEach { (name, why) -> log.warn("Personal text {} left out: {}", name, why) }
        result.info.questions
    }

    /** What the files did: the settings with them applied, and why each file left out was left out. */
    data class Result(val info: ApplicationInfo, val applied: List<String>, val skipped: Map<String, String>)

    /** [files] by name, as read; a prompt that would not save from Settings is skipped too. */
    fun apply(files: Map<String, String>, info: ApplicationInfo): Result {
        var questions = info.questions
        val applied = ArrayList<String>()
        val skipped = LinkedHashMap<String, String>()
        for ((name, raw) in files.toSortedMap()) {
            val target = TARGETS[name]
            if (target == null) {
                skipped[name] = "not a known file"
                continue
            }
            val text = raw.removePrefix("\uFEFF").trim()
            val problem = target.part?.let { part -> if (text.isEmpty()) "empty: the prompt is left as it is" else promptProblem(part, text) }
            if (problem != null) {
                skipped[name] = problem
                continue
            }
            questions = target.set(questions, text)
            applied += name
        }
        return Result(info.copy(questions = questions), applied, skipped)
    }
}
