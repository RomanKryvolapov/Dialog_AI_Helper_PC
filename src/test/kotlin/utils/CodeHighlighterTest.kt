package utils

import kotlin.test.Test
import kotlin.test.assertEquals

class CodeHighlighterTest {

    private fun kinds(code: String, language: String) =
        CodeHighlighter.highlight(code, language).filter { it.kind != CodeTokenKind.PLAIN }.map { it.kind to it.text }

    @Test
    fun `python keywords strings comments numbers and calls`() {
        val code = "def first(text: str):\n    # one pass\n    return next((c for c in text if n == 1), \"x\")"
        assertEquals(
            listOf(
                CodeTokenKind.KEYWORD to "def", CodeTokenKind.FUNCTION to "first", CodeTokenKind.COMMENT to "# one pass",
                CodeTokenKind.KEYWORD to "return", CodeTokenKind.FUNCTION to "next", CodeTokenKind.KEYWORD to "for",
                CodeTokenKind.KEYWORD to "in", CodeTokenKind.KEYWORD to "if", CodeTokenKind.NUMBER to "1",
                CodeTokenKind.STRING to "\"x\"",
            ),
            kinds(code, "python"),
        )
    }

    @Test
    fun `the tokens put back together are the code unchanged`() {
        val code = "SELECT id FROM orders -- recent\nWHERE note = 'it''s' /* x */ AND n2 > 10;\nval s = \"\"\"a\nb\"\"\" // end"
        listOf("sql", "kotlin", "python", "", "unknown").forEach { language ->
            assertEquals(code, CodeHighlighter.highlight(code, language).joinToString("") { it.text }, language)
        }
    }

    @Test
    fun `sql keywords ignore case, a name with digits is not a number`() {
        assertEquals(
            listOf(CodeTokenKind.KEYWORD to "SELECT", CodeTokenKind.KEYWORD to "from", CodeTokenKind.STRING to "'a'"),
            kinds("SELECT col2 from t1 = 'a'", "sql"),
        )
    }

    @Test
    fun `an unclosed string or comment runs to the end without losing text`() {
        assertEquals(listOf(CodeTokenKind.STRING to "\"abc"), kinds("\"abc", "js"))
        assertEquals(listOf(CodeTokenKind.COMMENT to "/* open"), kinds("/* open", "java"))
    }
}
