package extensions

import COLOUR_BLUE
import COLOUR_GREEN
import COLOUR_RED
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.control.TextInputControl
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

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

fun VBox.addTextFieldWithButtons(
    fieldText: String,
    lines: Int = 1,
    onSave: (String) -> Unit
) {
    val field = if(lines == 1){
        TextField().apply {
            text =fieldText
        }
    } else {
        TextArea().apply {
            isWrapText = true
            prefRowCount = lines
            text = fieldText
        }
    }
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
            style = """
                -fx-background-color: $COLOUR_BLUE;
            """.trimIndent()
            setOnAction {
                val content = ClipboardContent()
                content.putString(field.text)
                Clipboard.getSystemClipboard().setContent(content)
            }
        }
        val pasteButton = Button("Paste").apply {
            prefWidthProperty().bind(buttonWidthBinding)
            style = """
                -fx-background-color: $COLOUR_GREEN;
            """.trimIndent()
            setOnAction {
                val clipboard = Clipboard.getSystemClipboard()
                if (clipboard.hasString()) field.text = clipboard.string
            }
        }
        val saveButton = Button("Save").apply {
            prefWidthProperty().bind(buttonWidthBinding)
            style = """
                -fx-background-color: $COLOUR_RED;
            """.trimIndent()
            setOnAction {
                onSave(field.text)
            }
        }
        children.addAll(copyButton, pasteButton, saveButton)
    }
    box.children.addAll(fieldContainer, buttonsContainer)
    children.addAll(box)
}

fun <T> VBox.addComboBox(
    items: List<T>,
    selectedItem: T?,
    toStringFn: (T) -> String,
    onSelected: (T) -> Unit
): ComboBox<T>  {
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
        if(selectedItem != null) {
            selectionModel.select(selectedItem)
        }
        setOnAction {
            val selected = selectionModel.selectedItem
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