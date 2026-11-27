package ui

import defaultApplicationInfo
import extensions.addCheckBox
import extensions.addComboBox
import extensions.addButtons
import extensions.addSection
import extensions.saveButton
import extensions.settingsTextArea
import extensions.showNote
import extensions.statusLabel
import extensions.settingsPage
import javafx.scene.control.CheckBox
import javafx.scene.layout.VBox
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.QuestionsSettings
import models.domain.textModels
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.LastRequests
import utils.PersonalTexts
import utils.PromptPart
import utils.QuestionPrompts

/**
 * Settings → Questions: the mode that answers the questions asked in the conversation — on or off (also the box in
 * the top bar), the model it uses, how many phrases and answers each request carries, and its prompt.
 */
class QuestionsPromptTab(
    private val preferencesRepository: PreferencesRepository,
    lastRequests: LastRequests,
) : BaseTab(preferencesRepository) {

    private val builtIn = defaultApplicationInfo.questions
    private var enabledBox: CheckBox? = null

    val content: VBox = settingsPage {
        addSection(
            title = "Questions: answers to the questions asked in the conversation",
            note = "With the mode on, every phrase heard also goes, with the phrases before it, the conversation summary " +
                "and the questions answered so far, to the model chosen below. It answers a technical question (what " +
                "something is, how it works, code to write, how to build or debug something) once it has been asked in " +
                "full: the gist, a few short points, and a code block when code helps. Questions about the user's own " +
                "work, past, personal facts, money, dates and plans, and small talk are left to the user; a follow-up is " +
                "answered knowing the earlier question and answer. Answer now in the top bar answers the latest question " +
                "the mode let pass, even with it off. A click on an answer on the Context screen opens the follow-up " +
                "questions the other side may ask next; picking one writes a fuller answer under it.",
        ) {
            enabledBox = addCheckBox(
                text = "Answer the questions (the Context box in the top bar)",
                checked = settings().enabled,
                onToggle = { on -> if (settings().enabled != on) save { it.copy(enabled = on) } },
            )
            val models = textModels()
            addComboBox(
                items = models,
                selectedItem = models.firstOrNull { it.id == settings().modelId },
                toStringFn = { it.title },
                onSelected = { model -> save { it.copy(modelId = model.id) } },
            )
        }
        addSection(
            title = "Model for the fuller answers",
            note = "The fuller answers are asked for by hand, so a slower, stronger model fits; the follow-up questions " +
                "use the model above.",
        ) {
            val models = textModels()
            addComboBox(
                items = models,
                selectedItem = models.firstOrNull { it.id == settings().expansionModelId },
                toStringFn = { it.title },
                onSelected = { model -> save { it.copy(expansionModelId = model.id) } },
            )
        }
        textSetting(
            title = "About me",
            note = "Background and skills the answers are pitched at; never retold as the user's own story. Kept on " +
                "this computer and sent with each Questions request to the model's provider. Also read from " +
                "personal/about_user.md in the app's folder, as long as nothing is saved here.",
            rows = 8,
            current = { settings().aboutUser },
        ) { text -> save { it.copy(aboutUser = text.ifBlank { PersonalTexts.questionsDefaults.aboutUser }) } }
        textSetting(
            title = "Topic of the calls",
            note = "Such as the main technology or programming language; code is written in it. Also read from " +
                "personal/topic.txt.",
            rows = 2,
            current = { settings().topic },
        ) { text -> save { it.copy(topic = text.ifBlank { PersonalTexts.questionsDefaults.topic }) } }
        addSection(
            title = "Language the question is shown in",
            note = "The question as it appears on the Context screen: in the language of the call, as it was asked, " +
                "or translated into the language chosen here.",
        ) {
            languageBox(callLanguage = true, current = { settings().questionDisplayLanguage }) { language -> save { it.copy(questionDisplayLanguage = language) } }
        }
        addSection(
            title = "Language the answer is shown in",
            note = "Russian by default, like the question. Both choices reach the model through " +
                "the {{question_language}} and {{answer_language}} placeholders of the instructions below, and apply " +
                "from the next question; the answers already on screen stay as they are.",
        ) {
            languageBox(callLanguage = false, current = { settings().answerDisplayLanguage }) { language -> save { it.copy(answerDisplayLanguage = language) } }
        }
        children.add(
            numberSetting(
                title = "Phrases each request carries",
                description = "The latest phrases, newest last. A question is often cut into several phrases (one with " +
                    "its set-up took five in tests); fewer than five loses long questions.",
                value = { settings().phrases },
                min = 1,
                max = 20,
            ) { n -> save { it.copy(phrases = n) } }
        )
        children.add(
            numberSetting(
                title = "Latest answers kept in full",
                description = "The questions found so far always go with each request, so none is answered twice; the " +
                    "latest this many also carry their answers, for a follow-up about a detail of one.",
                value = { settings().fullAnswers },
                min = 0,
                max = 20,
            ) { n -> save { it.copy(fullAnswers = n) } }
        )
        children.add(
            PromptPartEditor(
                title = "Instructions (system message)",
                description = "What counts as a question, when to wait for the rest of one, and the reply: a <status> line " +
                    "(answer, update, wait, answered or none), then for an answer or an update <question> and <answer>. The app reads these tags.",
                part = PromptPart.QUESTIONS_INSTRUCTIONS,
                lines = 24,
                builtIn = builtIn.system,
                current = { settings().system },
                save = { text -> save { it.copy(system = text) } },
            ).node
        )
        children.add(
            PromptPartEditor(
                title = "Message",
                description = "Sent after the instructions. When a question seems unfinished and no phrase follows for " +
                    "3 seconds, the same request goes once more with <silence>3 s</silence> at the end.",
                part = PromptPart.QUESTIONS_MESSAGE,
                lines = 14,
                builtIn = builtIn.user,
                current = { settings().user },
                save = { text -> save { it.copy(user = text) } },
            ).node
        )
        children.add(
            lastRequestView(
                title = "Last questions request",
                description = "Exactly as it was sent, placeholders filled in.",
                requests = lastRequests.questions,
                empty = "No phrase checked for questions since the app started.",
            )
        )
        addSection(
            title = "Follow-up questions and fuller answers",
            note = "A click on an answer offers the follow-up questions the other side may ask next (asked for as soon " +
                "as the answer is complete); picking one writes a fuller answer under the short one. Any of these four " +
                "parts can also come from a file in personal/, as long as nothing is saved here.",
        )
        children.add(
            PromptPartEditor(
                title = "Follow-ups: instructions",
                description = "Asks for 3-5 follow-up questions, one <q>…</q> per line; the app reads these tags.",
                part = PromptPart.ANSWER_LANGUAGE_INSTRUCTIONS,
                lines = 12,
                builtIn = builtIn.followUpsSystem,
                current = { settings().followUpsSystem },
                save = { text -> save { it.copy(followUpsSystem = text) } },
            ).node
        )
        children.add(
            PromptPartEditor(
                title = "Follow-ups: message",
                description = "Sent after the instructions, with the question and its answer.",
                part = PromptPart.FOLLOW_UPS_MESSAGE,
                lines = 12,
                builtIn = builtIn.followUpsUser,
                current = { settings().followUpsUser },
                save = { text -> save { it.copy(followUpsUser = text) } },
            ).node
        )
        children.add(
            PromptPartEditor(
                title = "Fuller answer: instructions",
                description = "How the fuller answer is written.",
                part = PromptPart.ANSWER_LANGUAGE_INSTRUCTIONS,
                lines = 14,
                builtIn = builtIn.expansionSystem,
                current = { settings().expansionSystem },
                save = { text -> save { it.copy(expansionSystem = text) } },
            ).node
        )
        children.add(
            PromptPartEditor(
                title = "Fuller answer: message",
                description = "Sent after the instructions, with the question, its answer and the follow-up picked.",
                part = PromptPart.EXPANSION_MESSAGE,
                lines = 14,
                builtIn = builtIn.expansionUser,
                current = { settings().expansionUser },
                save = { text -> save { it.copy(expansionUser = text) } },
            ).node
        )
        children.add(
            lastRequestView(
                title = "Last follow-ups request",
                description = "Exactly as it was sent, placeholders filled in.",
                requests = lastRequests.followUps,
                empty = "No follow-up questions asked for since the app started.",
            )
        )
        children.add(
            lastRequestView(
                title = "Last fuller answer request",
                description = "Exactly as it was sent, placeholders filled in.",
                requests = lastRequests.expansion,
                empty = "No fuller answer asked for since the app started.",
            )
        )
        // The top bar's box changes the same setting.
        mainThreadScope.launch {
            preferencesRepository.appInfoFlow.map { it.questions.enabled }.distinctUntilChanged().collect { on ->
                enabledBox?.let { if (it.isSelected != on) it.isSelected = on }
            }
        }
    }

    /**
     * A display-language choice. Only the question's offers the call's own language: it is saved blank, and a blank
     * setting reads back as its default, which for the answer is English.
     */
    private fun VBox.languageBox(callLanguage: Boolean, current: () -> String, onPicked: (String) -> Unit) {
        val languages = (if (callLanguage) listOf("") else emptyList()) + QuestionPrompts.DISPLAY_LANGUAGES
        addComboBox(
            items = languages,
            selectedItem = languages.firstOrNull { it == current() },
            toStringFn = { it.ifBlank { "The language of the call" } },
            onSelected = { language -> if (current() != language) onPicked(language) },
        )
    }

    /** Free text saved with its button; saved blank, the file in personal/ applies again (or nothing). */
    private fun VBox.textSetting(title: String, note: String, rows: Int, current: () -> String, onSave: (String) -> Unit) {
        addSection(title = title, note = note) {
            val area = settingsTextArea(current(), rows)
            children.add(area)
            val status = statusLabel(area)
            addButtons(
                saveButton {
                    onSave(area.text.trim())
                    status.showNote("Saved")
                },
                status,
            )
        }
    }

    private fun settings(): QuestionsSettings = getAppInfo().questions

    private fun save(change: (QuestionsSettings) -> QuestionsSettings) {
        val appInfo = getAppInfo()
        saveAppInfo(appInfo.copy(questions = change(appInfo.questions)))
    }
}
