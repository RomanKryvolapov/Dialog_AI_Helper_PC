package utils

import kotlinx.coroutines.delay
import models.domain.LlmModel
import models.domain.LlmModelEngine
import models.domain.ModelParameters
import models.domain.ModelPrompt
import models.domain.QuestionsSettings
import models.domain.effectiveParameters
import models.domain.resolveLlmModel
import repository.CloudRepository
import repository.PreferencesRepository
import repository.streamText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onStart

/**
 * The two requests behind an answer on the Questions screen: the follow-up questions the other side may ask next
 * about it, and a fuller answer that covers the one the user picks. Both carry what the questions request carries
 * about the user and the topic, so a fuller answer stays consistent with the short one.
 */
object AnswerExpansionPrompts {

    const val MAX_FOLLOW_UPS = 5

    val FOLLOW_UPS_INSTRUCTIONS = """
You help the user during a live call. The other side asked the user the question in <question>, and the user got the short suggested answer in <answer>. List the follow-up questions the other side is most likely to ask next about this answer: the ones where the user would need more depth, such as how it works inside, trade-offs and alternatives, edge cases, performance, or a concrete example or code.

<about_user> holds facts about the user, <topic> what the call is about, <summary> the conversation so far; any of them may be empty.

Only technical follow-ups: never about the user's own projects, companies or past. Give 3 to 5 questions, the most likely first. Each is short (under 12 words), specific to this question and answer, and different from the others. Write them in {{answer_language}}; code identifiers stay as they are.

Reply with one <q>question</q> per line and nothing else.
""".trimIndent()

    val FOLLOW_UPS_MESSAGE = """
<about_user>
{{about_user}}
</about_user>

<topic>
{{topic}}
</topic>

<summary>
{{summary}}
</summary>

<question>
{{question}}
</question>

<answer>
{{answer}}
</answer>
""".trimIndent()

    val EXPANSION_INSTRUCTIONS = """
You help the user during a live call. The other side asked the user the question in <question>, and the user got the short suggested answer in <answer>. Now the user wants a fuller answer that also covers the point in <follow_up>. The user reads it on screen and uses it in the conversation.

<about_user> holds facts about the user, <topic> what the call is about, <summary> the conversation so far; any of them may be empty.

Write in {{answer_language}}, the way an experienced engineer explains it to a colleague out loud: plain everyday words, short sentences of up to about 12 words, no textbook definitions, no bookish or official style, no filler. Simple, yet concrete: every phrase carries a fact the user can use, such as the exact library, function or keyword to name, a number, the complexity, the alternative; a term that is not obvious gets a few plain words of explanation. Stay consistent with <answer>. Shape:
- one short sentence that answers <follow_up> directly;
- then 3-5 points on lines starting with "- ", each one short phrase with one concrete fact: how it works, trade-offs, pitfalls, when to use what;
- when code helps, a short working example as a fenced code block (```python and so on), in the language of <topic> unless the question names another; code, comments and identifiers in English.
About 60-120 words besides code. No headings, no bold, no preamble, no closing summary. Use <about_user> only to pitch the answer at the user's level and stack, never to talk about their past.
""".trimIndent()

    val EXPANSION_MESSAGE = """
<about_user>
{{about_user}}
</about_user>

<topic>
{{topic}}
</topic>

<summary>
{{summary}}
</summary>

<question>
{{question}}
</question>

<answer>
{{answer}}
</answer>

<follow_up>
{{follow_up}}
</follow_up>
""".trimIndent()

    private val FOLLOW_UP = Regex("""<q>(.*?)</q>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    /** The follow-up questions in a reply, at most [MAX_FOLLOW_UPS], blank and repeated ones left out. */
    fun parseFollowUps(reply: String): List<String> =
        FOLLOW_UP.findAll(reply).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_FOLLOW_UPS).toList()

    fun followUpsRequest(settings: QuestionsSettings, summary: String, question: String, answer: String) = ModelPrompt(
        system = fillTemplate(settings.followUpsSystem, language(settings)),
        user = fillTemplate(settings.followUpsUser, blocks(settings, summary, question, answer)),
    )

    fun expansionRequest(settings: QuestionsSettings, summary: String, question: String, answer: String, followUp: String) =
        ModelPrompt(
            system = fillTemplate(settings.expansionSystem, language(settings)),
            user = fillTemplate(
                settings.expansionUser,
                blocks(settings, summary, question, answer) + (PromptVariables.FOLLOW_UP to followUp),
            ),
        )

    private fun language(settings: QuestionsSettings) =
        mapOf(PromptVariables.ANSWER_LANGUAGE to QuestionPrompts.languageFor(settings.answerDisplayLanguage))

    private fun blocks(settings: QuestionsSettings, summary: String, question: String, answer: String) = mapOf(
        PromptVariables.ABOUT_USER to settings.aboutUser.ifBlank { "(not given)" },
        PromptVariables.TOPIC to settings.topic.ifBlank { "(not given)" },
        PromptVariables.SUMMARY to summary.ifBlank { "(none yet)" },
        PromptVariables.QUESTION to question,
        PromptVariables.ANSWER to answer,
    )
}

/**
 * Sends the [AnswerExpansionPrompts] requests: the follow-ups to the Questions mode's own model, which is chosen for
 * speed, and the fuller answer to the one chosen for it.
 */
class AnswerExpander(
    private val cloud: CloudRepository,
    private val preferences: PreferencesRepository,
    private val rateLimiters: RateLimiters,
    private val lastRequests: LastRequests,
) {

    private companion object {
        const val FOLLOW_UPS_TOKENS = 300
        const val EXPANSION_TOKENS = 1_500
        /** A user waits on the screen for this: a longer rate-limit wait is reported instead. */
        const val LONGEST_WAIT_MILLIS = 10_000L
    }

    /** 3–5 follow-up questions for [answer]; throws with a reason the user can read when there are none. */
    suspend fun followUps(question: String, answer: String): List<String> {
        val settings = preferences.getAppInfo().questions
        val prompt = AnswerExpansionPrompts.followUpsRequest(settings, lastRequests.summary.value, question, answer)
        val reply = send(settings.modelId, prompt, FOLLOW_UPS_TOKENS) { lastRequests.followUpsSent(it) }.last()
        return AnswerExpansionPrompts.parseFollowUps(reply).ifEmpty { throw IllegalStateException("the model suggested nothing") }
    }

    /** The fuller answer as it is written; each emission is the whole text so far. */
    fun expand(question: String, answer: String, followUp: String): Flow<String> = flow {
        val settings = preferences.getAppInfo().questions
        val prompt = AnswerExpansionPrompts.expansionRequest(settings, lastRequests.summary.value, question, answer, followUp)
        send(settings.expansionModelId, prompt, EXPANSION_TOKENS) { lastRequests.expansionSent(it) }.collect { emit(it) }
    }

    private suspend fun send(modelId: String, prompt: ModelPrompt, replyTokens: Int, record: (LastRequests.Sent) -> Unit): Flow<String> {
        val appInfo = preferences.getAppInfo()
        val model = resolveLlmModel(modelId)?.takeIf { it.readsText }
            ?: throw IllegalStateException("the model chosen in Settings → Questions ($modelId) cannot answer text")
        val key = when (model.engine) {
            LlmModelEngine.GOOGLE -> appInfo.googleCloudToken
            LlmModelEngine.OPENAI -> appInfo.openAiToken
            LlmModelEngine.CLAUDE -> appInfo.claudeToken
        }.ifBlank { throw IllegalStateException("no ${model.engine.type} API key") }
        val limiter = rateLimiters.forModel(model)
        while (true) {
            val wait = limiter.tryReserve((prompt.system.length + prompt.user.length) / 4 + replyTokens)
            if (wait == 0L) break
            if (wait > LONGEST_WAIT_MILLIS) throw IllegalStateException("${model.title} is at its rate limit for ${wait / 1000} s")
            delay(wait)
        }
        val chosen = appInfo.modelParameters[model.id] ?: ModelParameters()
        val parameters = effectiveParameters(model.parameterSupport, chosen.copy(maxOutputTokens = chosen.maxOutputTokens ?: replyTokens))
        return cloud.streamText(model, key, prompt, parameters).onStart { record(LastRequests.Sent(model.title, prompt)) }
    }
}

/** A piece of a model's text: prose, or the inside of a fenced code block with its language (may be blank). */
data class TextPart(val text: String, val code: Boolean, val language: String = "")

/**
 * [text] cut at its ``` fences into prose and code. A fence still open, as in an answer being written, makes the rest
 * code. Blank prose between parts is dropped.
 */
fun splitCodeBlocks(text: String): List<TextPart> {
    val parts = ArrayList<TextPart>()
    var rest = text
    while (true) {
        val open = rest.indexOf("```")
        if (open < 0) break
        rest.substring(0, open).trim().takeIf { it.isNotEmpty() }?.let { parts += TextPart(it, code = false) }
        val afterFence = rest.substring(open + 3)
        val lineEnd = afterFence.indexOf('\n').let { if (it < 0) afterFence.length else it }
        val firstLine = afterFence.substring(0, lineEnd)
        // Only a single word after the fence names the language; anything more (```sql select 1```) is code.
        val named = firstLine.isNotBlank() && !firstLine.trim().contains(' ') && !firstLine.contains("```")
        val language = if (named) firstLine.trim() else ""
        val body = if (named || firstLine.isBlank()) afterFence.substring(minOf(lineEnd + 1, afterFence.length)) else afterFence.trimStart()
        val close = body.indexOf("```")
        if (close < 0) {
            parts += TextPart(body.trimEnd(), code = true, language = language)
            return parts
        }
        parts += TextPart(body.substring(0, close).trimEnd(), code = true, language = language)
        rest = body.substring(close + 3)
    }
    rest.trim().takeIf { it.isNotEmpty() }?.let { parts += TextPart(it, code = false) }
    return parts
}
