package extensions

import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import models.common.TypeEnum
import models.common.TypeEnumInt
import java.io.File

fun String.normalizeAndRemoveEmptyLines(): String {
    return lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
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