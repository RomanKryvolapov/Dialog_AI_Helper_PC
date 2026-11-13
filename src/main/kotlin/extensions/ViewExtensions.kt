package extensions

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import kotlinx.coroutines.launch
import mainThreadScope
import java.io.File

// The Settings pages share one layout: a left-aligned column of sections, each a title, the grey note under it
// when there is one, the control, then a row of buttons of one width. The widths and the type are in styles.css;
// the gaps are the constants below and the button colours are the COLOUR_* constants of Consts.kt.

private const val PAGE_GAP = 32.0
/** The gap between the sections of a group (see [addGroup]). */
const val GROUP_GAP = 24.0
private const val SECTION_GAP = 8.0
private const val BUTTON_GAP = 12.0
private const val ERROR_STYLE = "error-label"

/** A Settings page: its sections one under another, left-aligned, with the same padding on every page. */
fun settingsPage(build: VBox.() -> Unit): VBox = VBox(PAGE_GAP).apply {
    padding = Insets(20.0, 24.0, 32.0, 24.0)
    build()
}

/** One setting: its [title], the grey [note] under it when there is one, then whatever [build] adds. */
fun settingsSection(title: String, note: String? = null, build: VBox.() -> Unit = {}): VBox =
    heading(title, note).apply(build)

fun VBox.addSection(title: String, note: String? = null, build: VBox.() -> Unit = {}): VBox =
    settingsSection(title, note, build).also { children.add(it) }

/**
 * Settings that belong together under one heading: a line sets the group off from what is above it, and the
 * sections [build] adds sit further apart than a setting's own parts.
 */
fun VBox.addGroup(title: String, note: String? = null, build: VBox.() -> Unit): VBox =
    VBox(GROUP_GAP, heading(title, note, group = true)).apply {
        styleClass.add("settings-group")
        build()
    }.also { children.add(it) }

private fun heading(title: String, note: String?, group: Boolean = false): VBox = VBox(SECTION_GAP).apply {
    children.add(titleLabel(title).apply { if (group) styleClass.add("group-title") })
    note?.let { children.add(noteLabel(it)) }
}

fun titleLabel(text: String): Label = Label(text).apply { styleClass.add("title-label") }

fun noteLabel(text: String): Label = Label(text).apply { styleClass.add("note-label") }

/** Where a setting says what became of it ("Saved", or why it was refused), beside its buttons. */
fun statusLabel(): Label = noteLabel("")

/** The same for what [field] holds: it goes blank as soon as the text changes again, so it never speaks of older text. */
fun statusLabel(field: TextInputControl): Label = statusLabel().also { status ->
    field.textProperty().addListener { _, _, _ -> status.showNote("") }
}

fun Label.showNote(text: String) {
    styleClass.remove(ERROR_STYLE)
    this.text = text
}

fun Label.showError(text: String) {
    if (ERROR_STYLE !in styleClass) styleClass.add(ERROR_STYLE)
    this.text = text
}

/** Every Settings button is as wide as the next; the colour says what it does: green saves, red clears, blue is the rest. */
fun settingsButton(title: String, colour: String, onClicked: () -> Unit): Button = Button(title).apply {
    styleClass.add("settings-button")
    style = "-fx-background-color: $colour;"
    setOnAction { onClicked() }
}

fun saveButton(onClicked: () -> Unit): Button = settingsButton("Save", COLOUR_GREEN, onClicked)

fun actionButton(title: String, onClicked: () -> Unit): Button = settingsButton(title, COLOUR_BLUE, onClicked)

fun copyButton(field: TextInputControl): Button = actionButton("Copy") {
    Clipboard.getSystemClipboard().setContent(ClipboardContent().apply { putString(field.text) })
}

fun pasteButton(field: TextInputControl): Button = actionButton("Paste") {
    val clipboard = Clipboard.getSystemClipboard()
    if (clipboard.hasString()) field.text = clipboard.string
}

/** The buttons of a setting, left-aligned under its control. */
fun VBox.addButtons(vararg nodes: Node): HBox =
    HBox(BUTTON_GAP, *nodes).apply { alignment = Pos.CENTER_LEFT }.also { children.add(it) }

private fun fieldStyle(short: Boolean) = if (short) "settings-field-short" else "settings-field"

/** A text field as wide as the column lets it be, or [short] for a number. */
fun settingsTextField(value: String, short: Boolean = false): TextField =
    TextField(value).apply { styleClass.add(fieldStyle(short)) }

fun VBox.addTextField(value: String): TextField = settingsTextField(value).also { children.add(it) }

fun settingsTextArea(text: String, rows: Int, editable: Boolean = true): TextArea = TextArea(text).apply {
    styleClass.add("settings-text-area")
    isWrapText = true
    isEditable = editable
    prefRowCount = rows
}

fun <T> VBox.addComboBox(
    items: List<T>,
    selectedItem: T?,
    toStringFn: (T) -> String,
    onSelected: (T) -> Unit = {},
    short: Boolean = false,
): ComboBox<T> {
    val comboBox = ComboBox(FXCollections.observableArrayList(items)).apply {
        styleClass.add(fieldStyle(short))
        cellFactory = javafx.util.Callback {
            object : ListCell<T>() {
                override fun updateItem(item: T?, empty: Boolean) {
                    super.updateItem(item, empty)
                    text = if (item == null || empty) null else toStringFn(item)
                }
            }
        }
        buttonCell = cellFactory.call(null)
        if (selectedItem != null) {
            selectionModel.select(selectedItem)
        }
        setOnAction {
            val selected = selectionModel.selectedItem
            if (selected == null) return@setOnAction
            onSelected(selected)
        }
        visibleRowCount = 50
    }
    children.add(comboBox)
    return comboBox
}

fun VBox.addCheckBox(
    text: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
): CheckBox {
    val checkBox = CheckBox(text).apply {
        isSelected = checked
        selectedProperty().addListener { _, _, isNowSelected -> onToggle(isNowSelected) }
    }
    children.add(checkBox)
    return checkBox
}

fun Stage.showAlert(
    alertTitle: String,
    alertContent: String,
    buttonText: String = "OK",
    onPositive: (() -> Unit)? = null
) {
    mainThreadScope.launch {
        val okButtonType = ButtonType(buttonText, ButtonBar.ButtonData.OK_DONE)
        val alert = Alert(Alert.AlertType.WARNING, "", okButtonType).apply {
            initOwner(this@showAlert)
            title = alertTitle
            headerText = null
            dialogPane.content = Label(alertContent).apply {
                isWrapText = true
                minHeight = Region.USE_PREF_SIZE
            }
        }

        val dialogButton = alert.dialogPane.lookupButton(okButtonType)
        dialogButton.style += """
            -fx-background-color: $COLOUR_GREEN;
            -fx-padding: 0 20 0 20;
        """.trimIndent()

        val result = alert.showAndWait()
        if (result.isPresent && result.get() == okButtonType) {
            onPositive?.invoke()
        }
    }
}

fun Stage.chooseDirectory(
    title: String
): File? {
    val directoryChooser = DirectoryChooser()
    directoryChooser.title = title
    val defaultDirectory = File(System.getProperty("user.dir"))
    if (defaultDirectory.exists()) {
        directoryChooser.initialDirectory = defaultDirectory
    }
    return directoryChooser.showDialog(this)
}