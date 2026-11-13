package ui

import defaultApplicationInfo
import extensions.addCheckBox
import extensions.addComboBox
import extensions.addSection
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
import utils.PromptPart

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
                "and the questions answered so far, to the model chosen below. It answers a new question once it has " +
                "been asked in full, in English and in the first person, so it can be said aloud; several questions get " +
                "one answer; a follow-up is answered knowing the earlier question and answer. Nothing appears on the " +
                "Questions screen when there is no question. The phrases come from the method in use: its recogniser, " +
                "or a voice model's own transcript. With Speechmatics' speaker labels each phrase says who spoke.",
        ) {
            enabledBox = addCheckBox(
                text = "Answer the questions (the Questions box in the top bar)",
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
                    "(answer, wait, answered or none), then for an answer <question> and <answer>. The app reads these tags.",
                part = PromptPart.INSTRUCTIONS,
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
        // The top bar's box changes the same setting.
        mainThreadScope.launch {
            preferencesRepository.appInfoFlow.map { it.questions.enabled }.distinctUntilChanged().collect { on ->
                enabledBox?.let { if (it.isSelected != on) it.isSelected = on }
            }
        }
    }

    private fun settings(): QuestionsSettings = getAppInfo().questions

    private fun save(change: (QuestionsSettings) -> QuestionsSettings) {
        val appInfo = getAppInfo()
        saveAppInfo(appInfo.copy(questions = change(appInfo.questions)))
    }
}
