package utils

/** What a piece of code is, for its colour. */
enum class CodeTokenKind { PLAIN, KEYWORD, STRING, COMMENT, NUMBER, FUNCTION }

data class CodeToken(val text: String, val kind: CodeTokenKind)

/**
 * A light syntax highlighter for the short code blocks in answers: keywords, strings, comments, numbers and called
 * names, by the fence's language. Not a parser: a misread token only gets the wrong colour, the text stays as it is.
 */
object CodeHighlighter {

    private class Language(val keywords: Set<String>, val lineComment: String?, val blockComment: Pair<String, String>?, val caseInsensitive: Boolean = false)

    private val C_LIKE_BLOCK = "/*" to "*/"

    private fun words(text: String) = text.split(' ').filter { it.isNotEmpty() }.toSet()

    private val PYTHON = Language(
        words("False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case self"),
        "#", null,
    )
    private val KOTLIN = Language(
        words("as break class continue do else false for fun if in interface is null object package return super this throw true try typealias val var when while by catch constructor data enum finally get import init inline internal lateinit open override private protected public sealed set suspend companion abstract const"),
        "//", C_LIKE_BLOCK,
    )
    private val JAVA = Language(
        words("abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long native new null package private protected public return short static super switch synchronized this throw throws transient true false try var void volatile while record"),
        "//", C_LIKE_BLOCK,
    )
    private val JS = Language(
        words("async await break case catch class const continue debugger default delete do else export extends false finally for from function if import in instanceof let new null of return super switch this throw true try typeof undefined var void while yield interface type enum implements private public readonly"),
        "//", C_LIKE_BLOCK,
    )
    private val GO = Language(
        words("break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var nil true false"),
        "//", C_LIKE_BLOCK,
    )
    private val CSHARP = Language(
        words("abstract as async await base bool break case catch class const continue decimal default delegate do double else enum false finally float for foreach if in int interface internal is lock long namespace new null object out override private protected public readonly ref return sealed static string struct switch this throw true try using var virtual void while"),
        "//", C_LIKE_BLOCK,
    )
    private val SQL = Language(
        words("select from where and or not insert into values update set delete create table index on join left right inner outer full group by order having limit offset as distinct union all exists in is null like between case when then else end with primary key foreign references drop alter add count sum avg min max over partition explain analyze desc asc"),
        "--", C_LIKE_BLOCK, caseInsensitive = true,
    )
    private val SHELL = Language(
        words("if then else elif fi for while do done case esac function in return export local echo cd sudo"),
        "#", null,
    )
    private val GENERIC = Language(PYTHON.keywords + KOTLIN.keywords + JS.keywords, "//", C_LIKE_BLOCK)

    private fun languageOf(name: String): Language = when (name.trim().lowercase()) {
        "python", "py" -> PYTHON
        "kotlin", "kt", "kts" -> KOTLIN
        "java" -> JAVA
        "javascript", "js", "typescript", "ts", "tsx", "jsx" -> JS
        "go", "golang" -> GO
        "csharp", "cs", "c#" -> CSHARP
        "sql", "postgresql", "postgres", "mysql" -> SQL
        "bash", "sh", "shell", "zsh", "powershell", "ps1" -> SHELL
        else -> GENERIC
    }

    fun highlight(code: String, language: String): List<CodeToken> {
        val lang = languageOf(language)
        val tokens = ArrayList<CodeToken>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) tokens += CodeToken(plain.toString(), CodeTokenKind.PLAIN)
            plain.clear()
        }
        fun add(text: String, kind: CodeTokenKind) {
            flush()
            tokens += CodeToken(text, kind)
        }
        var i = 0
        while (i < code.length) {
            val c = code[i]
            val lineComment = lang.lineComment
            val block = lang.blockComment
            when {
                lineComment != null && code.startsWith(lineComment, i) -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                    add(code.substring(i, end), CodeTokenKind.COMMENT)
                    i = end
                }
                block != null && code.startsWith(block.first, i) -> {
                    val close = code.indexOf(block.second, i + block.first.length)
                    val end = if (close < 0) code.length else close + block.second.length
                    add(code.substring(i, end), CodeTokenKind.COMMENT)
                    i = end
                }
                c == '"' || c == '\'' || c == '`' -> {
                    val triple = code.startsWith("$c$c$c", i)
                    val quote = if (triple) "$c$c$c" else "$c"
                    var j = i + quote.length
                    while (j < code.length && !code.startsWith(quote, j)) {
                        if (!triple && code[j] == '\n') break
                        j += if (code[j] == '\\') 2 else 1
                    }
                    val end = if (j < code.length && code.startsWith(quote, j)) j + quote.length else minOf(j, code.length)
                    add(code.substring(i, end), CodeTokenKind.STRING)
                    i = end
                }
                c.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit() && code[i - 1] != '_') -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                    add(code.substring(i, j), CodeTokenKind.NUMBER)
                    i = j
                }
                c.isLetter() || c == '_' -> {
                    var j = i
                    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    val word = code.substring(i, j)
                    val key = if (lang.caseInsensitive) word.lowercase() else word
                    val kind = when {
                        key in lang.keywords -> CodeTokenKind.KEYWORD
                        j < code.length && code[j] == '(' -> CodeTokenKind.FUNCTION
                        else -> CodeTokenKind.PLAIN
                    }
                    if (kind == CodeTokenKind.PLAIN) plain.append(word) else add(word, kind)
                    i = j
                }
                else -> {
                    plain.append(c)
                    i++
                }
            }
        }
        flush()
        return tokens
    }
}
