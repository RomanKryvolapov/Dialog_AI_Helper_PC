package ui

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.util.StringConverter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mainThreadScope
import models.domain.Hearing
import models.domain.LlmModel
import models.domain.TranslationMethod
import models.domain.allHearings
import models.domain.hearing
import models.domain.textModels
import repository.PreferencesRepository
import ui.base.BaseTab
import utils.ActiveMethod

/**
 * The translation method chain on Settings → General: three numbered rows, method 1 first, then 2 and 3
 * when the one before fails. Each row has two levels: how the speech is heard — a model that hears audio
 * itself, or a speech recogniser — and, for a recogniser only, the model that translates its text.
 *
 * Any method can be a reserve: when the dialog switches to it, the audio input is rebuilt for it (see
 * [ActiveMethod]). The method the dialog uses now is outlined in green; one it left after a failure in orange,
 * its tooltip saying why.
 */
class MethodChainSelector(
    preferencesRepository: PreferencesRepository,
    private val activeMethod: ActiveMethod,
) : BaseTab(preferencesRepository) {

    companion object {
        private const val NONE = "— none —"
        private const val ACTIVE_COLOUR = "#4CAF50"
        private const val LEFT_COLOUR = "#E0A030"
        private const val HEARING_WIDTH_SHARE = 0.34
        private const val TRANSLATOR_WIDTH_SHARE = 0.24
        private const val NUMBER_WIDTH = 24.0
        private const val ROW_SPACING = 10.0
        private val SLOT_HINTS = listOf(
            "Method 1: translates every phrase while it works",
            "Method 2: takes over for the rest of the dialog when method 1 fails or cannot keep up",
            "Method 3: takes over for the rest of the dialog when method 2 fails too",
        )
    }

    private val translators = textModels()

    /** What each row shows, as last filled from the settings. */
    private val shown = arrayOfNulls<TranslationMethod>(TranslationMethod.MAX_CHAIN)

    /** Set while the rows are filled from the settings, so that is not taken for a choice. */
    private var refreshing = false

    private val hearingCombos: List<ComboBox<Hearing?>> = List(TranslationMethod.MAX_CHAIN) { slot ->
        ComboBox<Hearing?>().apply {
            // Method 1 must hear somehow; a reserve may be left empty.
            items.setAll(if (slot == 0) allHearings() else listOf<Hearing?>(null) + allHearings())
            converter = titles { it?.title ?: NONE }
            // An empty value shows the prompt, not the converter's text.
            promptText = NONE
            cellFactory = javafx.util.Callback { titledCell { it?.title ?: NONE } }
            setOnAction { onEdited() }
        }
    }

    private val translatorCombos: List<ComboBox<LlmModel?>> = List(TranslationMethod.MAX_CHAIN) {
        ComboBox(FXCollections.observableArrayList<LlmModel?>(translators)).apply {
            visibleRowCount = translators.size
            converter = titles { it?.title ?: "" }
            cellFactory = javafx.util.Callback { titledCell { it?.title ?: "" } }
            setOnAction { onEdited() }
        }
    }

    val node: VBox = VBox(4.0).apply {
        for (slot in 0 until TranslationMethod.MAX_CHAIN) {
            children += HBox(ROW_SPACING).apply {
                alignment = Pos.CENTER
                padding = Insets(0.0, 20.0, 0.0, 20.0)
                val number = Label("${slot + 1}").apply { styleClass.add("chain-number") }
                val arrow = Label("→").apply { styleClass.add("chain-number") }
                // As wide as the number, so the combos stay centred like the other settings.
                val balance = Region().apply { minWidth = NUMBER_WIDTH; maxWidth = NUMBER_WIDTH }
                hearingCombos[slot].prefWidthProperty().bind(widthProperty().multiply(HEARING_WIDTH_SHARE))
                translatorCombos[slot].prefWidthProperty().bind(widthProperty().multiply(TRANSLATOR_WIDTH_SHARE))
                children.addAll(number, hearingCombos[slot], arrow, translatorCombos[slot], balance)
            }
        }
    }

    init {
        mainThreadScope.launch {
            preferencesRepository.appInfoFlow
                .map { it.translationMethods }
                .distinctUntilChanged()
                .collect { refresh(it) }
        }
        mainThreadScope.launch { activeMethod.flow.collect { paint(it) } }
    }

    private fun <T> titledCell(title: (T?) -> String) = object : ListCell<T?>() {
        override fun updateItem(item: T?, empty: Boolean) {
            super.updateItem(item, empty)
            text = if (empty) null else title(item)
        }
    }

    /** Also what the closed box shows: through the converter when its value is not in the list. */
    private fun <T> titles(title: (T?) -> String) = object : StringConverter<T?>() {
        override fun toString(item: T?): String = title(item)
        override fun fromString(text: String?): T? = null
    }

    private fun refresh(chain: List<TranslationMethod>) {
        refreshing = true
        try {
            for (slot in 0 until TranslationMethod.MAX_CHAIN) {
                val method = chain.getOrNull(slot)
                shown[slot] = method
                val hearingCombo = hearingCombos[slot]
                hearingCombo.visibleRowCount = hearingCombo.items.size
                // Set as the value: selecting the "— none —" entry, a null item, does not change the value of
                // a box that held a method the new list no longer has.
                hearingCombo.value = method?.hearing?.let { h -> hearingCombo.items.firstOrNull { it.sameAs(h) } }
                val translatorCombo = translatorCombos[slot]
                val translator = method?.takeUnless { it.voice }?.model
                translatorCombo.value = translator?.let { t -> translators.firstOrNull { it.id == t.id } }
                translatorCombo.isDisable = translator == null
                translatorCombo.promptText = if (method?.voice == true) "— the same model translates —" else ""
            }
        } finally {
            refreshing = false
        }
        paint(activeMethod.flow.value)
    }

    /**
     * A row was edited. Read once the lists have settled — picking an item can pass through a cleared
     * selection or report the same item twice — and rebuild the whole chain from what the rows show, so
     * a repeated event changes nothing. Empty rows close up, and a recogniser picked without a translator gets
     * the one its row had, or the first.
     */
    private fun onEdited() {
        if (refreshing) return
        Platform.runLater {
            val methods = (0 until TranslationMethod.MAX_CHAIN).mapNotNull { slot ->
                when (val hearing = hearingCombos[slot].value) {
                    null -> null
                    is Hearing.Voice -> TranslationMethod(hearing.model, recognizer = null)
                    is Hearing.Recognition -> TranslationMethod(
                        model = translatorCombos[slot].value
                            ?: shown[slot]?.model?.takeIf { it.readsText }
                            ?: translators.first(),
                        recognizer = hearing.recognizer,
                    )
                }
            }.distinctBy { it.key }
            val primary = methods.firstOrNull()
            val current = getAppInfo()
            if (primary == null) {
                refresh(current.translationMethods)
                return@runLater
            }
            val chain = methods.take(TranslationMethod.MAX_CHAIN)
            if (chain.map { it.key } != current.translationMethods.map { it.key }) {
                saveAppInfo(current.copy(translationMethods = chain))
            }
            refresh(chain)
        }
    }

    private fun paint(state: ActiveMethod.State) {
        for (slot in 0 until TranslationMethod.MAX_CHAIN) {
            val key = shown[slot]?.key
            val left = key?.let { state.left[it] }
            val colour = when {
                key != null && slot == state.index.coerceAtMost(shown.count { it != null } - 1) -> ACTIVE_COLOUR
                left != null -> LEFT_COLOUR
                else -> null
            }
            val style = colour?.let { "-fx-border-color: $it; -fx-border-width: 2; -fx-border-radius: 3;" } ?: ""
            hearingCombos[slot].style = style
            translatorCombos[slot].style = style
            val hint = SLOT_HINTS[slot]
            val tooltip = Tooltip(
                when {
                    colour == ACTIVE_COLOUR -> "$hint\nIn use now"
                    left != null -> "$hint\nLeft in this dialog: $left"
                    else -> hint
                }
            )
            hearingCombos[slot].tooltip = tooltip
            translatorCombos[slot].tooltip = tooltip
        }
    }

    /** Hearings compare by model id: the default chain's model carries a different description. */
    private fun Hearing?.sameAs(other: Hearing): Boolean = when (this) {
        is Hearing.Voice -> other is Hearing.Voice && other.model.id == model.id
        is Hearing.Recognition -> other is Hearing.Recognition && other.recognizer == recognizer
        null -> false
    }
}
