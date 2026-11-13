package utils

import backgroundThreadScope
import extensions.normalizeAndRemoveEmptyLines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import java.io.File
import java.io.IOException

class WhisperUtilImpl(
    private val preferencesRepository: PreferencesRepository,
) : WhisperUtil {

    private val log = LoggerFactory.getLogger("WhisperUtil")

    @Volatile
    private var modelPath = ""

    @Volatile
    private var maxConcurrentProcesses = 5


    private data class ProcessEntry(val process: Process, val job: Job)

    private val activeProcesses = ArrayDeque<ProcessEntry>()
    private val activeProcessesLock = Any()

    init {
        backgroundThreadScope.launch {
            preferencesRepository.appInfoFlow.collect { appInfo ->
                modelPath = appInfo.whisperModelPath
                val limit = appInfo.whisperModelConfig.maxConcurrentProcesses.coerceAtLeast(1)
                maxConcurrentProcesses = limit
                enforceLimit(limit)
            }
        }
    }

    override suspend fun recognize(wavFile: File, language: String): WhisperResult = try {
        runWhisper(wavFile, language)
    } catch (e: CancellationException) {
        // The caller itself was cancelled: pass that on. Otherwise only this Whisper process was evicted by
        // the concurrency limit, which is a failed transcription, not a cancellation.
        currentCoroutineContext().ensureActive()
        WhisperResult.Failed
    }

    override suspend fun transcribe(wav: ByteArray, language: String): WhisperResult {
        val file = try {
            withContext(Dispatchers.IO) { File.createTempFile("phrase-", ".wav").apply { writeBytes(wav) } }
        } catch (e: IOException) {
            log.error("Could not write the phrase for Whisper: ${e.message}", e)
            return WhisperResult.Failed
        }
        return recognize(file, language)
    }

    /** Runs the one-shot Whisper script on [wavFile] (deleted afterwards). */
    private suspend fun runWhisper(wavFile: File, language: String): WhisperResult = withContext(Dispatchers.IO) {
        log.debug("run whisper on ${wavFile.name}")
        try {
            val projectRoot = File("").absolutePath
            val venvPythonPath = "$projectRoot/python/.venv/Scripts/python.exe"
            val scriptPath = "$projectRoot/python/whisper_util.py"

            val pythonExecutable = File(venvPythonPath)
            if (!pythonExecutable.exists()) {
                log.error("Python executable not found at: $venvPythonPath")
                return@withContext WhisperResult.Failed
            }

            // stdout and stderr stay apart: stdout carries only the phrase's text, stderr the script's own
            // messages (a GPU attempt that failed before the CPU took over, a traceback), which are logged.
            val processBuilder = ProcessBuilder(
                pythonExecutable.absolutePath,
                "-Xutf8",
                scriptPath,
                wavFile.absolutePath,
                modelPath,
                language,
            ).apply {
                directory(File(projectRoot))
            }

            val process: Process = try {
                processBuilder.start()
            } catch (e: Exception) {
                log.error("Failed to start whisper process: ${e.message}", e)
                return@withContext WhisperResult.Failed
            }

            val entry = ProcessEntry(process, coroutineContext[Job]!!)
            val evicted = synchronized(activeProcessesLock) {
                activeProcesses.addLast(entry)
                val out = mutableListOf<ProcessEntry>()
                while (activeProcesses.size > maxConcurrentProcesses) {
                    out += activeProcesses.removeFirst()
                }
                out
            }
            evicted.forEach { killEntry(it, "limit reached") }

            // Drained alongside stdout (unread, a long traceback would fill its pipe and stall the script) and
            // outside this job, so a run the concurrency limit evicts still gets its messages logged.
            val messages = StringBuffer()
            val messagesReader = backgroundThreadScope.launch {
                try {
                    process.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { messages.appendLine(it) }
                } catch (e: IOException) {
                    messages.appendLine("(the rest could not be read: ${e.message})")
                }
            }
            var exitCode: Int? = null
            var output = ""
            try {
                output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                exitCode = process.waitFor()
                if (exitCode != 0) {
                    WhisperResult.Failed
                } else {
                    val text = output
                        .replace(Regex("""(\.\s*){2,}"""), "")
                        .normalizeAndRemoveEmptyLines()
                    log.debug("whisper text: $text")
                    val speech = WhisperHallucinations.clean(text)
                    when {
                        text.isBlank() -> {
                            log.warn("Whisper produced empty text for ${wavFile.name}")
                            WhisperResult.Silence
                        }
                        speech == null -> {
                            log.info("Whisper heard only \"$text\" in ${wavFile.name}, a phrase it makes up on silence and noise: dropped")
                            WhisperResult.Silence
                        }
                        else -> {
                            if (speech != text) log.info("Whisper in {}: subtitle lines or a loop dropped, \"{}\" became \"{}\"", wavFile.name, text, speech)
                            WhisperResult.Heard(speech)
                        }
                    }
                }
            } finally {
                // Off the list first: an eviction landing while the messages are awaited would cancel a
                // finished run.
                synchronized(activeProcessesLock) {
                    activeProcesses.remove(entry)
                }
                withContext(NonCancellable) { messagesReader.join() }
                logRun(wavFile, exitCode, messages.toString().trim(), output.trim())
            }
        } catch (e: CancellationException) {
            log.warn("Whisper job cancelled for ${wavFile.name}")
            throw e
        } catch (e: Exception) {
            log.error("Exception during whisper execution: ${e.message}", e)
            WhisperResult.Failed
        } finally {
            if (!wavFile.delete()) {
                log.warn("Failed to delete chunk ${wavFile.name}")
            }
        }
    }

    /** What one run said about itself: its stderr, and stdout too when it failed ([exitCode] null: it never finished). */
    private fun logRun(wavFile: File, exitCode: Int?, messages: String, output: String) {
        if (exitCode == 0) {
            if (messages.isNotEmpty()) log.info("Whisper messages for ${wavFile.name}:\n$messages")
            return
        }
        val outcome = if (exitCode == null) "did not finish" else "failed (exit $exitCode)"
        val details = listOf(messages, output).filter { it.isNotEmpty() }.joinToString("\n")
        if (details.isEmpty()) {
            log.warn("Whisper $outcome for ${wavFile.name} with no output")
        } else {
            log.warn("Whisper $outcome for ${wavFile.name}:\n$details")
        }
    }

    private fun enforceLimit(limit: Int) {
        val evicted = synchronized(activeProcessesLock) {
            val out = mutableListOf<ProcessEntry>()
            while (activeProcesses.size > limit) {
                out += activeProcesses.removeFirst()
            }
            out
        }
        evicted.forEach { killEntry(it, "limit lowered to $limit") }
    }

    private fun killEntry(entry: ProcessEntry, reason: String) {
        log.warn("Killing whisper process (pid=${entry.process.pid()}): $reason")
        runCatching { entry.process.destroyForcibly() }
        entry.job.cancel()
    }

}
