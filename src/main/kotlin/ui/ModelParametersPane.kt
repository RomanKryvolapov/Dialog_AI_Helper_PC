package ui

import extensions.GROUP_GAP
import extensions.actionButton
import extensions.addButtons
import extensions.addCheckBox
import extensions.addComboBox
import extensions.addGroup
import extensions.addSection
import extensions.noteLabel
import extensions.saveButton
import extensions.settingsPage
import extensions.settingsSection
import extensions.settingsTextField
import extensions.showError
import extensions.showNote
import extensions.statusLabel
import javafx.scene.control.ComboBox
import javafx.scene.control.TextField
import javafx.scene.layout.VBox
import models.domain.LlmModel
import models.domain.LlmModelEngine
import models.domain.ModelParameters
import models.domain.allLlmModels
import models.domain.effectiveParameters
import repository.PreferencesRepository
import ui.base.BaseTab

/**
 * Settings → LLM: the request settings of each model. Only the settings a model accepts are offered (see
 * ModelParameterSupport), an empty field takes its default, and the line at the bottom says what is sent.
 */
class ModelParametersPane(preferencesRepository: PreferencesRepository) : BaseTab(preferencesRepository) {

    private val models = allLlmModels()
    private var model: LlmModel = models.first()
    private val fields = VBox(GROUP_GAP)

    val content: VBox = settingsPage {
        addSection("Responses") {
            addCheckBox(
                text = "Use streaming response (show translation as it is generated)",
                checked = getAppInfo().useStreamingResponse,
                onToggle = { checked -> saveAppInfo(getAppInfo().copy(useStreamingResponse = checked)) },
            )
        }
        addGroup(
            title = "Model parameters",
            note = "What each request to a model carries, translations, summaries and questions alike. An empty field takes " +
                "the model's default, and a model is only offered the settings it accepts, as test requests to " +
                "Google and OpenAI showed (Claude's follow Anthropic's documentation, untested): a setting a " +
                "model refuses would fail every request.",
        ) {
            addComboBox(
                items = models,
                selectedItem = model,
                toStringFn = { it.title },
                onSelected = {
                    model = it
                    rebuild()
                },
            )
            children.add(fields)
            rebuild()
        }
    }

    private fun rebuild() {
        fields.children.clear()
        val support = model.parameterSupport
        val chosen = getAppInfo().modelParameters[model.id] ?: ModelParameters()
        val status = statusLabel()

        val temperature = if (support.temperature) numberField(
            "Temperature",
            "0 to 2: lower keeps the wording steadier, higher varies it. Default: " +
                (support.defaultTemperature?.toString() ?: "the provider's"),
            chosen.temperature?.toString(),
        ) else null
        val topP = if (support.topP) numberField("Top P", "Above 0, up to 1. Default: the provider's.", chosen.topP?.toString()) else null
        val topK = if (support.topK) numberField("Top K", "A whole number from 1. Default: the provider's.", chosen.topK?.toString()) else null
        val maxTokens = numberField(
            "Max output tokens",
            "The longest answer allowed; a longer one is cut off. Default: the provider's.",
            chosen.maxOutputTokens?.toString(),
        )
        val thinkingTitle = when (model.engine) {
            LlmModelEngine.OPENAI -> "Reasoning effort"
            else -> "Thinking"
        }
        val thinking = if (support.thinkingLevels.isNotEmpty()) choice(
            thinkingTitle,
            "How much the model thinks before answering: more is slower and costs output tokens, and a " +
                "translation needs little.",
            support.thinkingLevels,
            support.defaultThinking,
            chosen.thinking,
        ) else null
        val verbosity = if (support.verbosityLevels.isNotEmpty()) choice(
            "Verbosity",
            "How wordy the answer is.",
            support.verbosityLevels,
            null,
            chosen.verbosity,
        ) else null

        listOfNotNull(temperature, topP, topK, maxTokens).forEach { fields.children.add(it.node) }
        thinking?.let { fields.children.add(it.node) }
        if (support.thinkingLevels.isEmpty() && support.defaultThinking != null) {
            fields.children.add(noteLabel("$thinkingTitle: ${support.defaultThinking}, the only level this model accepts."))
        }
        verbosity?.let { fields.children.add(it.node) }
        support.samplingOnlyAtThinking?.let { level ->
            fields.children.add(
                noteLabel("Temperature and top P are sent only with ${thinkingTitle.lowercase()} \"$level\": at any other the model refuses them.")
            )
        }
        val sent = noteLabel("")
        fun showSent() {
            sent.text = "Sent with each request now: " + describe(effectiveParameters(support, getAppInfo().modelParameters[model.id]))
        }

        val save = saveButton {
            val parsed = try {
                ModelParameters(
                    temperature = temperature?.double(0.0, 2.0, "Temperature"),
                    topP = topP?.double(0.0, 1.0, "Top P", minExclusive = true),
                    topK = topK?.int(1, 1_000, "Top K"),
                    maxOutputTokens = maxTokens.int(1, 1_000_000, "Max output tokens"),
                    thinking = thinking?.selected(),
                    verbosity = verbosity?.selected(),
                )
            } catch (e: IllegalArgumentException) {
                status.showError(e.message.orEmpty())
                return@saveButton
            }
            saveParameters(parsed)
            status.showNote("Saved")
            showSent()
        }
        val reset = actionButton("Reset to defaults") {
            saveParameters(ModelParameters())
            rebuild()
        }
        fields.addButtons(save, reset, status)
        fields.children.add(sent)
        showSent()
    }

    private fun saveParameters(parameters: ModelParameters) {
        val appInfo = getAppInfo()
        val all = appInfo.modelParameters - model.id
        saveAppInfo(appInfo.copy(modelParameters = if (parameters == ModelParameters()) all else all + (model.id to parameters)))
    }

    private fun describe(p: ModelParameters): String = listOfNotNull(
        p.temperature?.let { "temperature $it" },
        p.topP?.let { "top P $it" },
        p.topK?.let { "top K $it" },
        p.maxOutputTokens?.let { "max output tokens $it" },
        p.thinking?.let { "${if (model.engine == LlmModelEngine.OPENAI) "reasoning effort" else "thinking"} $it" },
        p.verbosity?.let { "verbosity $it" },
    ).joinToString(", ").ifEmpty { "nothing, the provider's defaults" }

    private class NumberField(val node: VBox, private val field: TextField) {
        fun double(min: Double, max: Double, name: String, minExclusive: Boolean = false): Double? {
            val text = field.text.trim().ifEmpty { return null }
            val value = text.replace(',', '.').toDoubleOrNull()
            require(value != null && value <= max && (if (minExclusive) value > min else value >= min)) {
                if (minExclusive) "$name: a number above $min, up to $max" else "$name: a number from $min to $max"
            }
            return value
        }

        fun int(min: Int, max: Int, name: String): Int? {
            val text = field.text.trim().ifEmpty { return null }
            val value = text.toIntOrNull()
            require(value != null && value in min..max) { "$name: a whole number from $min to $max" }
            return value
        }
    }

    private fun numberField(title: String, description: String, value: String?): NumberField {
        val field = settingsTextField(value.orEmpty(), short = true).apply { promptText = "default" }
        return NumberField(settingsSection(title, description) { children.add(field) }, field)
    }

    private class Choice(val node: VBox, private val box: ComboBox<String>) {
        /** Null for the first entry, the model's default. */
        fun selected(): String? = box.value?.takeIf { box.selectionModel.selectedIndex > 0 }
    }

    private fun choice(title: String, description: String, levels: List<String>, default: String?, value: String?): Choice {
        val defaultEntry = "default" + (default?.let { " ($it)" } ?: "")
        lateinit var box: ComboBox<String>
        val node = settingsSection(title, description) {
            box = addComboBox(
                items = listOf(defaultEntry) + levels,
                selectedItem = value?.takeIf { it in levels } ?: defaultEntry,
                toStringFn = { it },
                short = true,
            )
        }
        return Choice(node, box)
    }
}
