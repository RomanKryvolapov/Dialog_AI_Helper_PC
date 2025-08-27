package extensions

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.control.TextInputControl
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import kotlinx.coroutines.NonCancellable.children
import kotlinx.coroutines.launch
import mainThreadScope
import java.io.File

fun VBox.addLabel(text: String) {
    children.add(Label(text).apply {
        maxWidth = Double.MAX_VALUE
    })
}


fun VBox.addTitleLabel(text: String) {
    children.add(Label(text).apply {
        maxWidth = Double.MAX_VALUE
        styleClass.add("title-label")
    })
}

fun VBox.addTextField(
    fieldText: String,
    lines: Int = 1,
): TextInputControl {
    val field = if (lines == 1) {
        TextField(fieldText)
    } else {
        TextArea(fieldText).apply {
            isWrapText = true
            prefRowCount = lines
        }
    }
    val fieldContainer = HBox(field).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        field.prefWidthProperty().bind(widthProperty().multiply(0.6))
    }
    children.add(fieldContainer)
    return field
}

fun VBox.addTextFieldWithSaveButton(
    fieldText: String,
    lines: Int = 1,
    buttonTitle: String,
    onClicked: (String) -> Unit
): TextInputControl {
    val field = if (lines == 1) {
        TextField(fieldText)
    } else {
        TextArea(fieldText).apply {
            isWrapText = true
            prefRowCount = lines
        }
    }
    val buttonWidthBinding = field.prefWidthProperty().divide(3)
    val button = Button(buttonTitle).apply {
        style = "-fx-background-color: $COLOUR_BLUE"
        prefWidthProperty().bind(buttonWidthBinding)
        setOnAction {
            onClicked(field.text)
        }
    }
    val buttonContainer = HBox(4.0).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        children.add(button)
        button.prefWidthProperty().bind(widthProperty().multiply(0.6))
    }
    val fieldContainer = HBox(field).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        field.prefWidthProperty().bind(widthProperty().multiply(0.6))
    }
    children.addAll(fieldContainer, buttonContainer)
    return field
}

fun VBox.addTextFieldWithCopyPasteActionButtons(
    fieldText: String,
    lines: Int = 1,
    buttonTitle: String = "Save",
    onClicked: (String) -> Unit
): TextInputControl {
    val field = if (lines == 1) {
        TextField(fieldText)
    } else {
        TextArea(fieldText).apply {
            isWrapText = true
            prefRowCount = lines
        }
    }
    val buttonWidthBinding = field.prefWidthProperty().divide(3)
    val copyButton = Button("Copy").apply {
        style = "-fx-background-color: $COLOUR_BLUE"
        prefWidthProperty().bind(buttonWidthBinding)
        setOnAction {
            val content = ClipboardContent()
            content.putString(field.text)
            Clipboard.getSystemClipboard().setContent(content)
        }
    }
    val pasteButton = Button("Paste").apply {
        style = "-fx-background-color: $COLOUR_GREEN"
        prefWidthProperty().bind(buttonWidthBinding)
        setOnAction {
            val clipboard = Clipboard.getSystemClipboard()
            if (clipboard.hasString()) field.text = clipboard.string
        }
    }
    val saveButton = Button(buttonTitle).apply {
        style = "-fx-background-color: $COLOUR_RED"
        prefWidthProperty().bind(buttonWidthBinding)
        setOnAction {
            onClicked(field.text)
        }
    }
    val buttonsContainer = HBox(4.0, copyButton, pasteButton, saveButton).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
    }
    val fieldContainer = HBox(field).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        field.prefWidthProperty().bind(widthProperty().multiply(0.6))
    }
    children.addAll(fieldContainer, buttonsContainer)
    return field
}

fun <T> VBox.addComboBox(
    items: List<T>,
    selectedItem: T?,
    toStringFn: (T) -> String,
    onSelected: (T) -> Unit
): ComboBox<T> {
    val comboBox = ComboBox(FXCollections.observableArrayList(items)).apply {
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
    val container = HBox(comboBox).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        comboBox.prefWidthProperty().bind(widthProperty().multiply(0.6))
    }
    children.add(container)
    return comboBox
}

fun VBox.addButton(
    title: String,
    bgColor: String,
    onClicked: () -> Unit
): Button {
    val button = Button(title).apply {
        style = """
            -fx-background-color: $bgColor;
        """.trimIndent()
        prefWidthProperty().bind(widthProperty().multiply(0.6))
        setOnAction {
            onClicked.invoke()
        }
    }
    val buttonContainer = HBox(4.0).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        children.add(button)
        button.prefWidthProperty().bind(widthProperty().multiply(0.6))
    }
    children.add(buttonContainer)
    return button
}

fun Stage.showAlert(
    alertTitle: String,
    alertContent: String,
) {
    mainThreadScope.launch {
        Alert(Alert.AlertType.WARNING).apply {
            initOwner(this@showAlert)
            title = alertTitle
            headerText = null
            contentText = alertContent
            showAndWait()
        }
    }
}

fun Stage.showAlertWithAction(
    alertTitle: String,
    alertContent: String,
    onPositive: () -> Unit
) {
    mainThreadScope.launch {
        val okButton = ButtonType("OK", ButtonBar.ButtonData.OK_DONE)
        val cancelButton = ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE)
        val alert = Alert(Alert.AlertType.CONFIRMATION, alertContent, okButton, cancelButton).apply {
            initOwner(this@showAlertWithAction)
            title = alertTitle
            headerText = null
        }
        val result = alert.showAndWait()
        if (result.isPresent && result.get() == okButton) {
            onPositive()
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