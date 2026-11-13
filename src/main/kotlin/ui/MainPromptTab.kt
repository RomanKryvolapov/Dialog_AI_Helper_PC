package ui

import defaultApplicationInfo
import extensions.addCheckBox
import extensions.addGroup
import extensions.addSection
import extensions.settingsPage
import javafx.scene.layout.VBox
import kotlinx.coroutines.flow.combine
import models.domain.PromptSettings
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.LastRequests
import utils.PromptPart
import utils.TranslationPrompts

/**
 * Settings → Main prompt: everything a request to translate a recognised phrase is made of, the running
 * summary of the conversation and the request that writes it, and the last requests as they were sent.
 */
class MainPromptTab(
    private val preferencesRepository: PreferencesRepository,
    lastRequests: LastRequests,
) : BaseTab(preferencesRepository) {

    private val builtIn = defaultApplicationInfo.prompts

    val content: VBox = settingsPage {
        addSection(
            title = "Main prompt: translating a phrase the speech recogniser wrote down",
            note = "Each phrase is one request. The instructions go first, as the system message, the same for every " +
                "phrase. Then the message: the running summary of the conversation, the phrases said just " +
                "before (context only, never translated again) and the phrase itself. The translation request " +
                "never writes the summary: a separate request does, further down this page. A voice model " +
                "hearing the recording has its own prompt (Voice prompt); the previous phrases and the summary " +
                "set here serve both.",
        )

        children.add(
            numberSetting(
                title = "Previous phrases sent with each phrase",
                description = "How many of the phrases said just before go into {{previous_phrases}}, each with " +
                    "the translation already given. They help with a sentence cut in the middle and with words " +
                    "the recogniser misheard; each one adds to every request's tokens. 0 sends none.",
                value = { prompts().previousPhrases },
                min = 0,
                max = 10,
            ) { n -> savePrompts { it.copy(previousPhrases = n) } }
        )

        addGroup("Translation request") {
            children.add(
                PromptPartEditor(
                    title = "Instructions (system message)",
                    description = "Who the model is, what it gets and what it answers. The same for every phrase, so " +
                        "it takes no placeholders.",
                    part = PromptPart.INSTRUCTIONS,
                    lines = 22,
                    builtIn = builtIn.text.system,
                    current = { prompts().text.system },
                    save = { text -> savePrompts { it.copy(text = it.text.copy(system = text)) } },
                ).node
            )
            children.add(
                PromptPartEditor(
                    title = "Message",
                    description = "Sent after the instructions, with the placeholders filled in.",
                    part = PromptPart.TEXT_MESSAGE,
                    lines = 12,
                    builtIn = builtIn.text.user,
                    current = { prompts().text.user },
                    save = { text -> savePrompts { it.copy(text = it.text.copy(user = text)) } },
                ).node
            )
            children.add(
                PromptPartEditor(
                    title = "Previous phrase format",
                    description = "How each previous phrase is written into {{previous_phrases}}, and each phrase " +
                        "into the summary request's {{new_phrases}}.",
                    part = PromptPart.PREVIOUS_PHRASE,
                    lines = 4,
                    builtIn = builtIn.text.previousPhrase,
                    current = { prompts().text.previousPhrase },
                    save = { text -> savePrompts { it.copy(text = it.text.copy(previousPhrase = text)) } },
                ).node
            )
        }

        addGroup(
            title = "Conversation summary: a request of its own",
            note = "The summary keeps what the previous phrases no longer show: the topic, who speaks and how they " +
                "address each other, names and terms with the Russian already chosen. It is written the first " +
                "time a phrase leaves the previous phrases, then again each time the set number of phrases " +
                "has left them since, by the model of the method in use when it reads text, else the first one in the chain that does. Each request gets the " +
                "summary so far and those phrases, and returns the new summary; it reaches the translations " +
                "through {{summary}}. Clear dialog starts it over.",
        ) {
            addCheckBox(
                text = "Keep a running summary of the conversation",
                checked = prompts().summary.enabled,
                onToggle = { on -> savePrompts { it.copy(summary = it.summary.copy(enabled = on)) } },
            )
            children.add(
                numberSetting(
                    title = "Rewrite the summary every … phrases",
                    description = "Fewer means a fresher summary and more requests. The phrases that left the " +
                        "previous phrases and wait for the next summary reach no request: up to about this many while " +
                        "each summary comes back before the next phrase, more while one is slow or has failed, or while " +
                        "an earlier phrase is still being recognised, until a summary takes them in.",
                    value = { prompts().summary.everyPhrases },
                    min = 1,
                    max = 50,
                ) { n -> savePrompts { it.copy(summary = it.summary.copy(everyPhrases = n)) } }
            )
            children.add(
                PromptPartEditor(
                    title = "Summary instructions (system message)",
                    description = "What the summary keeps and how long it may be.",
                    part = PromptPart.INSTRUCTIONS,
                    lines = 18,
                    builtIn = builtIn.summary.system,
                    current = { prompts().summary.system },
                    save = { text -> savePrompts { it.copy(summary = it.summary.copy(system = text)) } },
                ).node
            )
            children.add(
                PromptPartEditor(
                    title = "Summary message",
                    description = "The summary so far and the phrases to fold into it.",
                    part = PromptPart.SUMMARY_MESSAGE,
                    lines = 8,
                    builtIn = builtIn.summary.user,
                    current = { prompts().summary.user },
                    save = { text -> savePrompts { it.copy(summary = it.summary.copy(user = text)) } },
                ).node
            )
        }

        addGroup("What the models got") {
            children.add(
                liveTextView(
                    title = "Summary now",
                    description = "What {{summary}} holds in the next request.",
                    text = combine(lastRequests.summary, preferencesRepository.appInfoFlow) { summary, appInfo ->
                        if (appInfo.prompts.summary.enabled) TranslationPrompts.summaryInRequest(appInfo.prompts, summary)
                        else "(off: no summary is written, and {{summary}} is sent empty)"
                    },
                    empty = "(none yet)",
                )
            )
            children.add(
                lastRequestView(
                    title = "Last translation request",
                    description = "Exactly as it was sent, placeholders filled in.",
                    requests = lastRequests.text,
                    empty = "No phrase translated with this prompt since the app started.",
                )
            )
            children.add(
                lastRequestView(
                    title = "Last summary request",
                    description = "Exactly as it was sent.",
                    requests = lastRequests.summaryRequest,
                    empty = "No summary written since the app started.",
                )
            )
        }
    }

    private fun prompts(): PromptSettings = getAppInfo().prompts

    private fun savePrompts(change: (PromptSettings) -> PromptSettings) {
        val appInfo = getAppInfo()
        saveAppInfo(appInfo.copy(prompts = change(appInfo.prompts)))
    }
}
