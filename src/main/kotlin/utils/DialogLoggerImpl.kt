package utils

import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DialogLoggerImpl : DialogLogger {

    companion object {
        private const val DIALOGS_DIR = "dialogs"
        private val FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        private val TIMECODE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    private val log = LoggerFactory.getLogger("DialogLogger")

    private val fileLock = Any()

    @Volatile
    private var questionsFile: File? = null

    @Volatile
    private var answersFile: File? = null

    override fun startSession() {
        synchronized(fileLock) {
            val dir = File(DIALOGS_DIR).apply { mkdirs() }
            val prefix = LocalDateTime.now().format(FILE_NAME_FORMATTER)
            val q = File(dir, "${prefix}_question.md")
            val a = File(dir, "${prefix}_answer.md")
            try {
                q.createNewFile()
                a.createNewFile()
            } catch (e: Exception) {
                log.error("Failed to create dialog files: ${e.message}", e)
                return
            }
            questionsFile = q
            answersFile = a
            log.debug("Dialog session started: $prefix")
        }
    }

    override fun logQuestion(text: String) {
        append(ensureFile { questionsFile }, text)
    }

    override fun logAnswer(text: String) {
        append(ensureFile { answersFile }, text)
    }

    private fun ensureFile(accessor: () -> File?): File? {
        var file = accessor()
        if (file == null) {
            startSession()
            file = accessor()
        }
        return file
    }

    private fun append(file: File?, text: String) {
        if (file == null) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val timecode = LocalDateTime.now().format(TIMECODE_FORMATTER)
        synchronized(fileLock) {
            try {
                file.appendText("$timecode: $trimmed\n\n")
            } catch (e: Exception) {
                log.error("Failed to append to ${file.name}: ${e.message}", e)
            }
        }
    }

}
