package ui

import defaultApplicationInfo
import extensions.addSection
import extensions.noteLabel
import extensions.settingsPage
import javafx.scene.layout.VBox
import models.domain.PromptSettings
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.LastRequests
import utils.PromptPart

/**
 * Settings → Voice prompt: everything a request to a voice model is made of — the model hears the recorded
 * phrase itself, writes down what was said and translates it in one answer — and the last one as it was sent.
 */
class VoicePromptTab(
    preferencesRepository: PreferencesRepository,
    lastRequests: LastRequests,
) : BaseTab(preferencesRepository) {

    private val builtIn = defaultApplicationInfo.prompts

    val content: VBox = settingsPage {
        addSection(
            title = "Voice prompt: a voice model hears the recording itself",
            note = "Used by voice methods (Gemini 3.5 Flash-Lite, GPT-Audio-1.5): the recorded phrase goes to the " +
                "model with the instructions and the message below, and the model writes down what was said " +
                "and translates it in one answer. How many previous phrases are sent and the conversation " +
                "summary are set on Main prompt and shared with it.",
        ) {
            children.add(
                noteLabel(
                    "The answer must keep its two lines, <said>…</said> and <translated>…</translated>: the app takes " +
                        "the phrase and its translation from them, and an answer without them counts as failed."
                )
            )
        }
        children.add(
            PromptPartEditor(
                title = "Instructions (system message)",
                description = "Who the model is, what it gets and the shape of its answer. The same for every " +
                    "phrase, so it takes no placeholders.",
                part = PromptPart.INSTRUCTIONS,
                lines = 24,
                builtIn = builtIn.voice.system,
                current = { prompts().voice.system },
                save = { text -> savePrompts { it.copy(voice = it.voice.copy(system = text)) } },
            ).node
        )
        children.add(
            PromptPartEditor(
                title = "Message",
                description = "Sent after the instructions; {{audio}} marks where the recording goes (at the end " +
                    "when it is left out).",
                part = PromptPart.VOICE_MESSAGE,
                lines = 12,
                builtIn = builtIn.voice.user,
                current = { prompts().voice.user },
                save = { text -> savePrompts { it.copy(voice = it.voice.copy(user = text)) } },
            ).node
        )
        children.add(
            PromptPartEditor(
                title = "Previous phrase format",
                description = "How each previous phrase is written into {{previous_phrases}}.",
                part = PromptPart.PREVIOUS_PHRASE,
                lines = 4,
                builtIn = builtIn.voice.previousPhrase,
                current = { prompts().voice.previousPhrase },
                save = { text -> savePrompts { it.copy(voice = it.voice.copy(previousPhrase = text)) } },
            ).node
        )
        children.add(
            lastRequestView(
                title = "Last voice request",
                description = "Exactly as it was sent, placeholders filled in; the recording itself is not shown.",
                requests = lastRequests.voice,
                empty = "No phrase sent to a voice model since the app started.",
            )
        )
    }

    private fun prompts(): PromptSettings = getAppInfo().prompts

    private fun savePrompts(change: (PromptSettings) -> PromptSettings) {
        val appInfo = getAppInfo()
        saveAppInfo(appInfo.copy(prompts = change(appInfo.prompts)))
    }
}
