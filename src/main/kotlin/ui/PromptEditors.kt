package ui

import VOICE_AUDIO_MARK
import extensions.actionButton
import extensions.addButtons
import extensions.noteLabel
import extensions.saveButton
import extensions.settingsSection
import extensions.settingsTextArea
import extensions.settingsTextField
import extensions.showError
import extensions.showNote
import extensions.statusLabel
import javafx.scene.layout.VBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import mainThreadScope
import utils.LastRequests
import utils.PromptPart
import utils.PromptVariables
import utils.promptProblem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SENT_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/**
 * One editable prompt part: what it is, the placeholders it takes, the text, and Save / Reset to the default
 * text. Save refuses text the part cannot use (see [promptProblem]), so a request never carries a placeholder
 * nobody fills in or lacks the one that brings in its input.
 */
class PromptPartEditor(
    title: String,
    description: String,
    private val part: PromptPart,
    lines: Int,
    private val builtIn: String,
    private val current: () -> String,
    private val save: (String) -> Unit,
) {
    private val area = settingsTextArea(current(), lines)
    private val status = statusLabel()

    val node: VBox = settingsSection(title, description) {
        if (part.allowed.isNotEmpty()) {
            children.add(noteLabel(part.allowed.joinToString("\n") { "{{$it}} — ${PromptVariables.descriptions[it]}" }))
        }
        children.add(area)
        addButtons(
            saveButton { onSave() },
            actionButton("Reset to default") {
                save(builtIn)
                area.text = builtIn
                showState()
            },
            status,
        )
        showState()
    }

    private fun onSave() {
        val problem = promptProblem(part, area.text)
        if (problem != null) {
            status.showError(problem)
            return
        }
        save(area.text)
        // A part saved blank is the built-in one again (the mapper reads blank as never edited).
        area.text = current()
        showState()
    }

    private fun showState() {
        status.showNote(if (current() == builtIn) "Default text" else "Edited")
    }
}

/** A whole number setting with its own Save; [min]..[max] is enforced, the reason shown next to the button. */
fun numberSetting(
    title: String,
    description: String,
    value: () -> Int,
    min: Int,
    max: Int = Int.MAX_VALUE,
    save: (Int) -> Unit,
): VBox = wholeNumberSetting(title, description, { value().toLong() }, min.toLong(), max.toLong()) { save(it.toInt()) }

/** The same for a time in milliseconds, which is not bounded above. */
fun millisecondsSetting(
    title: String,
    description: String,
    value: () -> Long,
    min: Long,
    save: (Long) -> Unit,
): VBox = wholeNumberSetting(title, description, value, min, Long.MAX_VALUE, save)

private fun wholeNumberSetting(
    title: String,
    description: String,
    value: () -> Long,
    min: Long,
    max: Long,
    save: (Long) -> Unit,
): VBox {
    val field = settingsTextField(value().toString(), short = true)
    val status = statusLabel(field)
    return settingsSection(title, description) {
        children.add(field)
        addButtons(
            saveButton {
                val number = field.text.trim().toLongOrNull()
                if (number == null || number !in min..max) {
                    status.showError(if (max >= Int.MAX_VALUE) "A whole number, $min or more" else "A whole number from $min to $max")
                    return@saveButton
                }
                save(number)
                field.text = value().toString()
                status.showNote("Saved")
            },
            status,
        )
    }
}

/**
 * The last request of one kind exactly as the model got it, kept up to date: the instructions, then the
 * message with every placeholder filled in.
 */
fun lastRequestView(title: String, description: String, requests: Flow<LastRequests.Sent?>, empty: String): VBox {
    val header = noteLabel(empty)
    val area = settingsTextArea("", rows = 16, editable = false)
    mainThreadScope.launch {
        requests.collect { sent ->
            if (sent == null) {
                header.text = empty
                area.text = ""
                return@collect
            }
            header.text = "Sent to ${sent.model} at ${SENT_AT.format(Instant.ofEpochMilli(sent.atMillis))}"
            area.text = buildString {
                append("— INSTRUCTIONS (system message) —\n")
                append(sent.prompt.system.ifBlank { "(none)" })
                append("\n\n— MESSAGE —\n")
                append(sent.prompt.user.replace(VOICE_AUDIO_MARK, "[the recording of the phrase]"))
            }
        }
    }
    return settingsSection(title, description) {
        children.add(header)
        children.add(area)
    }
}

/** A read-only text kept up to date from [text]. */
fun liveTextView(title: String, description: String, text: Flow<String>, empty: String): VBox {
    val area = settingsTextArea("", rows = 6, editable = false)
    mainThreadScope.launch { text.collect { area.text = it.ifBlank { empty } } }
    return settingsSection(title, description) { children.add(area) }
}
