package utils

fun String.normalizeAndRemoveEmptyLines(): String {
    return lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}