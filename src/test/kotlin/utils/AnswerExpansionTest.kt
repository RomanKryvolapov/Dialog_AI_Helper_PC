package utils

import defaultApplicationInfo
import models.domain.ConversationTurn
import models.domain.QuestionsSettings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnswerExpansionTest {

    @Test
    fun `prose and fenced code come apart, the language kept`() {
        val text = "Use a generator.\n\n```python\ndef evens(n):\n    yield from range(0, n, 2)\n```\nIt is lazy."
        assertEquals(
            listOf(
                TextPart("Use a generator.", code = false),
                TextPart("def evens(n):\n    yield from range(0, n, 2)", code = true, language = "python"),
                TextPart("It is lazy.", code = false),
            ),
            splitCodeBlocks(text),
        )
    }

    @Test
    fun `a fence still open while the answer is written makes the rest code`() {
        assertEquals(
            listOf(TextPart("Like this:", code = false), TextPart("x = [i for i in", code = true, language = "py")),
            splitCodeBlocks("Like this:\n```py\nx = [i for i in"),
        )
        assertEquals(listOf(TextPart("", code = true, language = "")), splitCodeBlocks("```"))
        assertEquals(listOf(TextPart("plain", code = false)), splitCodeBlocks("plain"))
        assertTrue(splitCodeBlocks("  ").isEmpty())
    }

    @Test
    fun `follow-ups are read from their tags, at most five, without blanks or repeats`() {
        val reply = "<q>Why a generator?</q>\n<q> </q>\n<q>Why a generator?</q>\n<q>Memory use?</q>\n<q>A</q><q>B</q><q>C</q><q>D</q>"
        assertEquals(listOf("Why a generator?", "Memory use?", "A", "B", "C"), AnswerExpansionPrompts.parseFollowUps(reply))
        assertTrue(AnswerExpansionPrompts.parseFollowUps("Sure! Here are some questions.").isEmpty())
    }

    @Test
    fun `the fuller answer request carries the user, the topic and the chosen follow-up`() {
        val settings = defaultApplicationInfo.questions.copy(aboutUser = "Builds RAG systems.", topic = "Python", answerDisplayLanguage = "Russian")
        val prompt = AnswerExpansionPrompts.expansionRequest(settings, "", "What is a GIL?", "A lock.", "Does asyncio avoid it?")
        assertTrue("in Russian" in prompt.system)
        assertTrue("<about_user>\nBuilds RAG systems.\n</about_user>" in prompt.user)
        assertTrue("<topic>\nPython\n</topic>" in prompt.user)
        assertTrue("<summary>\n(none yet)\n</summary>" in prompt.user)
        assertTrue("<follow_up>\nDoes asyncio avoid it?\n</follow_up>" in prompt.user)
        assertTrue("{{" !in prompt.system + prompt.user)
    }

    @Test
    fun `the questions request carries the user and the topic, or says they were not given`() {
        val turn = ConversationTurn(source = "What is a decorator?")
        val given = QuestionPrompts.request(defaultApplicationInfo.questions.copy(aboutUser = "Ten years of Python.", topic = "Python"), "", emptyList(), listOf(turn), false, false)
        assertTrue("<about_user>\nTen years of Python.\n</about_user>" in given.user)
        assertTrue("<topic>\nPython\n</topic>" in given.user)
        val empty = QuestionPrompts.request(defaultApplicationInfo.questions, "", emptyList(), listOf(turn), false, false)
        assertTrue("<about_user>\n(not given)\n</about_user>" in empty.user)
        assertTrue("<heard>\nthe other side, and the user may be mixed in: the recogniser does not tell\n</heard>" in empty.user)
        val both = QuestionPrompts.request(defaultApplicationInfo.questions, "", emptyList(), listOf(turn), false, false, userHeard = true)
        assertTrue("<heard>\nboth the other side and the user\n</heard>" in both.user)
        val paused = ConversationTurn(source = "Why?").apply { pauseBeforeMillis = 1_250 }
        val timed = QuestionPrompts.request(defaultApplicationInfo.questions, "", emptyList(), listOf(turn, paused), false, false)
        assertTrue("<speech>\nWhat is a decorator?\n(after 1.3 s) Why?\n</speech>" in timed.user, timed.user)
    }

    @Test
    fun `personal texts fill their settings, and a broken or unknown file is skipped with the reason`() {
        val result = PersonalTexts.apply(
            mapOf(
                "about_user.md" to "﻿  Ten years of Python.\n",
                "topic.txt" to "Python",
                "expansion_message.md" to "no placeholders at all",
                "notes.txt" to "whatever",
            ),
            defaultApplicationInfo,
        )
        assertEquals("Ten years of Python.", result.info.questions.aboutUser)
        assertEquals("Python", result.info.questions.topic)
        assertEquals(AnswerExpansionPrompts.EXPANSION_MESSAGE, result.info.questions.expansionUser)
        assertEquals(listOf("about_user.md", "topic.txt"), result.applied)
        assertEquals(setOf("expansion_message.md", "notes.txt"), result.skipped.keys)
        val blank = PersonalTexts.apply(mapOf("questions_instructions.md" to "  \n"), defaultApplicationInfo)
        assertEquals(QuestionPrompts.INSTRUCTIONS, blank.info.questions.system)
        assertEquals(setOf("questions_instructions.md"), blank.skipped.keys)
    }

    @Test
    fun `the built-in prompts pass their own checks`() {
        val s: QuestionsSettings = defaultApplicationInfo.questions
        listOf(
            PromptPart.QUESTIONS_INSTRUCTIONS to s.system, PromptPart.QUESTIONS_MESSAGE to s.user,
            PromptPart.ANSWER_LANGUAGE_INSTRUCTIONS to s.followUpsSystem, PromptPart.FOLLOW_UPS_MESSAGE to s.followUpsUser,
            PromptPart.ANSWER_LANGUAGE_INSTRUCTIONS to s.expansionSystem, PromptPart.EXPANSION_MESSAGE to s.expansionUser,
        ).forEach { (part, text) -> assertEquals(null, promptProblem(part, text), part.name) }
    }

    @Test
    fun `a fence on one line keeps its code`() {
        val parts = splitCodeBlocks("Run it: ```select count(*) from t``` and done.")
        assertEquals(listOf(TextPart("Run it:", false), TextPart("select count(*) from t", true), TextPart("and done.", false)), parts)
        assertEquals(TextPart("print(1)", true, "python"), splitCodeBlocks("```python\nprint(1)\n```").single())
    }
}
