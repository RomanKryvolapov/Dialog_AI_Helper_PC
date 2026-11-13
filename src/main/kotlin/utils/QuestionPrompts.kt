package utils

import models.domain.ConversationTurn
import models.domain.ModelPrompt
import models.domain.QuestionsSettings

/**
 * The Questions mode's request and how its answer is read. The built-in texts are the design that answered every
 * question on the phrase that finished it in emulated calls on GPT-6.1 Terra (2026-11-10): the
 * instructions first; then the conversation summary, the questions found so far with their answers, and the latest
 * phrases, newest last. The model starts its reply with a status line, so "no question" is an answer, not silence.
 */
object QuestionPrompts {

    const val SILENCE_SECONDS = 3

    /**
     * A question this close (shared words) to one already answered is the same text again and is not shown twice. Only
     * near-identical text: the model's own "answered" status handles a question reworded, and a lower bar silenced
     * different ones ("process vs thread" against "thread vs coroutine" shares 80% of the words).
     */
    const val SAME_QUESTION_OVERLAP = 0.9

    val INSTRUCTIONS = """
You help the user answer questions during a live call held in English. You get the call's transcript from a speech recognizer and you answer the questions the other side asks the user. The user reads your answer on screen and gives it as their reply.

The transcript is imperfect. Words can be misheard: when a word does not fit, pick the intended one from the topic (for example "get hub" is GitHub, "oh auth" is OAuth). Punctuation can be wrong. A long sentence is cut into several consecutive phrases at arbitrary points: a phrase that does not end with . ? or ! was cut in the middle, and its sentence continues in the next phrase.

Every call gives you these blocks:
<summary> what the conversation has been about so far (may be empty or a little behind);
<answered> the questions already found in this conversation, each with the answer given (may be empty);
<speech> the latest recognized phrases, oldest first. The LAST line is the newest phrase;
<silence> (only sometimes present) how long the speaker has been silent after the newest phrase; then the newest phrase is final, not cut: answer any question that can be understood, even if the phrase lacks . ? or !.

Speakers. Normally only the other side of the call is heard. Sometimes the user is heard as well, mixed in, with or without speaker labels (S1, S2: given in order of first speech, so a label does not tell who is who). Tell them apart by what they say: the other side asks about the user's knowledge and experience and reacts to the answers; the user answers in the first person and may ask the other side something back.

A QUESTION is a request addressed to the user for knowledge, experience, an opinion, an approach or a story, in any form: "What is...", "How would you...", "Can you explain...", "Tell me about...", "Walk me through...", "Describe...", "What do you think about...?". A follow-up that only makes sense together with the previous question and answer ("Why did you pick that one?", "How would that scale?") is a new question: restore what it refers to from <answered>.

NOT a question to answer: greetings, audio or screen-sharing checks, scheduling and other small talk; reactions to an answer ("Okay, makes sense."); statements the other side makes about themselves, their company or their plans; a question the other side asks and then answers themselves; anything the user says: their answers, rhetorical questions inside them, their own questions to the other side. A scenario or task the other side is only setting up ("Imagine...", "Suppose...", "Here is a problem...") is not yet a question: the question itself follows.

Decide about the NEWEST phrase, using the earlier phrases as context:
IF <speech> holds no question to the user -> no output.
IF a question is not finished (the newest phrase breaks off mid-sentence, or the other side is still setting the question up) -> no output; the next call will see the rest.
IF every question in <speech> has the same meaning as one in <answered> -> no output. That covers a question asked again or reworded, a question still sitting in <speech> after it was answered, and a newest phrase that only finishes or refines a question already answered (a clause, a detail or a missing word added to it).
ELSE -> answer. If several new finished questions are open, one answer covers all of them, in the order asked.

Answer in English, in the first person, as the user would say it: correct and concrete, 2-4 short sentences or at most 4 short bullet points (about 60 words). For a question about the user's own experience, which you cannot know, give a short outline with [placeholders] for the personal facts instead of inventing them.

Start the reply with a status line:
<status>answer</status> a new finished question was found; then give
<question>the question(s) as asked: complete and standalone, misheard words corrected, a follow-up restated in full; several questions numbered 1) 2)</question>
<answer>the answer</answer>
<status>wait</status> a question is still being asked; write nothing else
<status>answered</status> the only questions are already in <answered>; write nothing else
<status>none</status> no question to the user; write nothing else
Write nothing else.
""".trimIndent()

    val MESSAGE = """
<summary>
{{summary}}
</summary>

<answered>
{{answered}}
</answered>

<speech>
{{speech}}
</speech>
""".trimIndent()

    /** One question found earlier, with its answer (null: older ones keep only the question). */
    data class Answered(val question: String, val answer: String?)

    fun request(settings: QuestionsSettings, summary: String, answered: List<Answered>, speech: List<ConversationTurn>, afterSilence: Boolean) =
        ModelPrompt(
            system = settings.system,
            user = fillTemplate(
                settings.user,
                mapOf(
                    PromptVariables.SUMMARY to summary.ifBlank { "(none yet)" },
                    PromptVariables.ANSWERED to answered.joinToString("\n") { entry ->
                        buildString {
                            append("<qa>\n<question>").append(entry.question).append("</question>\n")
                            entry.answer?.let { append("<answer>").append(it).append("</answer>\n") }
                            append("</qa>")
                        }
                    }.ifEmpty { "(none yet)" },
                    PromptVariables.SPEECH to speech.joinToString("\n") { turn ->
                        turn.speaker?.let { "$it: ${turn.source}" } ?: turn.source
                    },
                ),
            ) + if (afterSilence) "\n\n<silence>$SILENCE_SECONDS s</silence>" else "",
        )

    /** What a reply says so far; [question] and [answer] are set once their tags have opened. */
    data class Reply(val status: String?, val question: String?, val questionComplete: Boolean, val answer: String?)

    private val STATUS = Regex("""<status>\s*([a-z]+)\s*</status>""", RegexOption.IGNORE_CASE)

    /** Reads a reply, whole or still streaming; anything after </answer> is ignored. */
    fun parse(reply: String): Reply {
        val status = STATUS.find(reply)?.groupValues?.get(1)?.lowercase()
        fun block(tag: String): Pair<String?, Boolean> {
            val start = reply.indexOf("<$tag>")
            if (start < 0) return null to false
            val from = start + tag.length + 2
            val end = reply.indexOf("</$tag>", from)
            return (if (end < 0) reply.substring(from) else reply.substring(from, end)).trim() to (end >= 0)
        }
        val (question, questionComplete) = block("question")
        val (answer, _) = block("answer")
        return Reply(status, question, questionComplete, answer)
    }

    /** Share of words two questions have in common (Jaccard over lowercase words). */
    fun overlap(a: String, b: String): Double {
        val wordsA = words(a)
        val wordsB = words(b)
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0
        return (wordsA intersect wordsB).size.toDouble() / (wordsA union wordsB).size
    }

    private fun words(text: String): Set<String> =
        text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.toSet()
}
