package utils

import backgroundThreadScope
import extensions.normalizeAndRemoveEmptyLines
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import java.io.BufferedReader
import java.io.File
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
    override var onFailureListener: (() -> Unit)? = null

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

    override fun runFasterWhisper(wavFile: File, language: String) {
        backgroundThreadScope.launch {
            log.debug("run whisper on ${wavFile.name}")
            var succeeded = false
            try {
                val projectRoot = File("").absolutePath
                val venvPythonPath = "$projectRoot/python/.venv/Scripts/python.exe"
                val scriptPath = "$projectRoot/python/whisper_util.py"

                val pythonExecutable = File(venvPythonPath)
                if (!pythonExecutable.exists()) {
                    log.error("Python executable not found at: $venvPythonPath")
                    return@launch
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
                    return@launch
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
                        log.warn("Whisper process exited with code $exitCode for ${wavFile.name}")
                    } else {
                        val text = result.toString()
                            .replace(Regex("""(\.\s*){2,}"""), "")
                            .normalizeAndRemoveEmptyLines()
                        log.debug("whisper text: $text")
                        if (text.isNotBlank()) {
                            succeeded = true
                            onResultListener?.invoke(text)
                        } else {
                            log.warn("Whisper produced empty text for ${wavFile.name}")
                        }
                    }
                } finally {
                    synchronized(activeProcessesLock) {
                        activeProcesses.remove(entry)
                    }
                    if (!wavFile.delete()) {
                        log.warn("Failed to delete chunk ${wavFile.name}")
                    }
                }
            } catch (e: CancellationException) {
                log.warn("Whisper job cancelled for ${wavFile.name}")
                throw e
            } catch (e: Exception) {
                log.error("Exception during whisper execution: ${e.message}", e)
            } finally {
                if (!succeeded) {
                    runCatching { onFailureListener?.invoke() }
                }
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
