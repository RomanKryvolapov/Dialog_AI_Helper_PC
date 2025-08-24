package utils

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import buttonStyle
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.TextInputControl
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import models.common.TypeEnum
import models.common.TypeEnumInt

fun String.normalizeAndRemoveEmptyLines(): String {
    return lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

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

fun VBox.createTextFieldRow(field: TextInputControl, onSave: (String) -> Unit) {
    val box = VBox(4.0)
    val fieldContainer = HBox(field).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        field.prefWidthProperty().bind(widthProperty().multiply(0.6))
    }
    val buttonsContainer = HBox(4.0).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        val buttonWidthBinding = field.prefWidthProperty().divide(3)
        val copyButton = Button("Copy").apply {
            prefWidthProperty().bind(buttonWidthBinding)
            style = buttonStyle(COLOUR_BLUE)
            setOnAction {
                val content = ClipboardContent()
                content.putString(field.text)
                Clipboard.getSystemClipboard().setContent(content)
            }
        }
        val pasteButton = Button("Paste").apply {
            prefWidthProperty().bind(buttonWidthBinding)
            style = buttonStyle(COLOUR_GREEN)
            setOnAction {
                val clipboard = Clipboard.getSystemClipboard()
                if (clipboard.hasString()) field.text = clipboard.string
            }
        }
        val saveButton = Button("Save").apply {
            prefWidthProperty().bind(buttonWidthBinding)
            style = buttonStyle(COLOUR_RED)
            setOnAction {
                onSave(field.text)
            }
        }
        children.addAll(copyButton, pasteButton, saveButton)
    }
    box.children.addAll(fieldContainer, buttonsContainer)
    children.addAll(box)
}

fun <T> VBox.addComboBoxSettingRowWithLabel(
    title: String,
    items: List<T>,
    selectedItem: T,
    toStringFn: (T) -> String,
    onSelected: (T) -> Unit
) {
    addTitleLabel(title)
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
        selectionModel.select(selectedItem)
        setOnAction {
            val selected = selectionModel.selectedItem
            onSelected(selected)
        }
    }
    val container = HBox(comboBox).apply {
        alignment = Pos.CENTER
        padding = Insets(0.0, 20.0, 0.0, 20.0)
        comboBox.prefWidthProperty().bind(widthProperty().multiply(0.6))
    }
    children.add(container)
}

inline fun <reified T : Enum<T>> getEnumValue(type: String): T? {
    val values = enumValues<T>()
    return values.firstOrNull {
        it is TypeEnum && (it as TypeEnum).type.equals(type, true)
    }
}

inline fun <reified T : Enum<T>> getEnumIntValue(type: Int): T? {
    val values = enumValues<T>()
    return values.firstOrNull {
        it is TypeEnumInt && (it as TypeEnumInt).type == type
    }
}
