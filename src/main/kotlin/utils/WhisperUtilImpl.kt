package utils

import backgroundThreadScope
import extensions.normalizeAndRemoveEmptyLines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class WhisperUtilImpl(
    private val preferencesRepository: PreferencesRepository,
) : WhisperUtil {

    private val log = LoggerFactory.getLogger("WhisperUtil")

    @Volatile
    private var modelPath = ""

    @Volatile
    private var maxConcurrentProcesses = 5

    override var onResultListener: ((String) -> Unit)? = null
    override var onFailureListener: ((whisperFailed: Boolean) -> Unit)? = null

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

    /** What one run of the script came to. */
    private sealed interface Outcome {
        data class Heard(val text: String) : Outcome
        /** It ran and heard no speech. */
        data object Silence : Outcome
        /** Whisper itself failed: no Python, the process did not start, crashed, or was cancelled. */
        data object Failed : Outcome
    }

    override fun runFasterWhisper(wavFile: File, language: String) {
        backgroundThreadScope.launch {
            var outcome: Outcome = Outcome.Failed
            try {
                outcome = runWhisper(wavFile, language)
            } finally {
                when (val done = outcome) {
                    is Outcome.Heard -> onResultListener?.invoke(done.text)
                    Outcome.Silence -> runCatching { onFailureListener?.invoke(false) }
                    Outcome.Failed -> runCatching { onFailureListener?.invoke(true) }
                }
            }
        }
    }

    override suspend fun transcribe(wav: ByteArray, language: String): String? {
        val file = try {
            withContext(Dispatchers.IO) { File.createTempFile("phrase-", ".wav").apply { writeBytes(wav) } }
        } catch (e: IOException) {
            log.error("Could not write the phrase for Whisper: ${e.message}", e)
            return null
        }
        return try {
            (runWhisper(file, language) as? Outcome.Heard)?.text
        } catch (e: CancellationException) {
            // The caller itself was cancelled: pass that on. Otherwise only this Whisper process was
            // evicted by the concurrency limit, which is a failed transcription, not a cancellation.
            currentCoroutineContext().ensureActive()
            null
        }
    }

    /** Runs the one-shot Whisper script on [wavFile] (deleted afterwards). */
    private suspend fun runWhisper(wavFile: File, language: String): Outcome = withContext(Dispatchers.IO) {
        log.debug("run whisper on ${wavFile.name}")
        try {
            val projectRoot = File("").absolutePath
            val venvPythonPath = "$projectRoot/python/.venv/Scripts/python.exe"
            val scriptPath = "$projectRoot/python/whisper_util.py"

            val pythonExecutable = File(venvPythonPath)
            if (!pythonExecutable.exists()) {
                log.error("Python executable not found at: $venvPythonPath")
                return@withContext Outcome.Failed
            }

            val processBuilder = ProcessBuilder(
                pythonExecutable.absolutePath,
                "-Xutf8",
                scriptPath,
                wavFile.absolutePath,
                modelPath,
                language,
            ).apply {
                directory(File(projectRoot))
                redirectErrorStream(true)
            }

            val process: Process = try {
                processBuilder.start()
            } catch (e: Exception) {
                log.error("Failed to start whisper process: ${e.message}", e)
                return@withContext Outcome.Failed
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

            try {
                val output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                val result = StringBuilder()
                var line: String?
                while (output.readLine().also { line = it } != null) {
                    result.appendLine(line)
                }
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    val details = result.toString().trim()
                    if (details.isNotBlank()) {
                        log.warn("Whisper failed (exit $exitCode) for ${wavFile.name}:\n$details")
                    } else {
                        log.warn("Whisper failed (exit $exitCode) for ${wavFile.name} with no output")
                    }
                    Outcome.Failed
                } else {
                    val text = result.toString()
                        .replace(Regex("""(\.\s*){2,}"""), "")
                        .normalizeAndRemoveEmptyLines()
                    log.debug("whisper text: $text")
                    if (text.isBlank()) {
                        log.warn("Whisper produced empty text for ${wavFile.name}")
                        Outcome.Silence
                    } else {
                        Outcome.Heard(text)
                    }
                }
            } finally {
                synchronized(activeProcessesLock) {
                    activeProcesses.remove(entry)
                }
            }
        } catch (e: CancellationException) {
            log.warn("Whisper job cancelled for ${wavFile.name}")
            throw e
        } catch (e: Exception) {
            log.error("Exception during whisper execution: ${e.message}", e)
            Outcome.Failed
        } finally {
            if (!wavFile.delete()) {
                log.warn("Failed to delete chunk ${wavFile.name}")
            }
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
