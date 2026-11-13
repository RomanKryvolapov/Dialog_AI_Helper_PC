package utils

import backgroundThreadScope
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Base64
import java.util.concurrent.TimeUnit

class WhisperStreamingUtilImpl(
    moshi: Moshi,
) : WhisperStreamingUtil {

    private val log = LoggerFactory.getLogger("WhisperStreamingUtil")
    private val lineAdapter = moshi.adapter(StreamLine::class.java)

    override var onPartialListener: ((String) -> Unit)? = null
    override var onFinalListener: ((String) -> Unit)? = null
    override var onFailureListener: ((String) -> Unit)? = null

    /** The process [stop] ended on purpose: its reader then going quiet is not a failure. */
    @Volatile
    private var stoppedProcess: Process? = null

    @Volatile
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private val writeLock = Any()

    data class StreamLine(val type: String?, val text: String?)

    override fun isRunning(): Boolean = process?.isAlive == true

    /**
     * A final made only of phrases Whisper makes up on silence and noise (see [WhisperHallucinations]) is
     * passed on empty: nothing to translate, and the row its partials opened is taken away.
     */
    private fun speechOf(text: String): String {
        if (text.isBlank()) return text
        val speech = WhisperHallucinations.clean(text)
        when {
            speech == null -> log.info("Whisper streaming heard only \"$text\", a phrase it makes up on silence and noise: dropped")
            speech != text -> log.info("Whisper streaming: subtitle lines or a loop dropped, \"{}\" became \"{}\"", text, speech)
        }
        return speech.orEmpty()
    }

    override fun start(modelPath: String, language: String, stepMillis: Long, minChars: Int, maxUtteranceMillis: Long) {
        stop()
        if (modelPath.isBlank()) {
            log.error("Whisper streaming: model path is empty")
            onFailureListener?.invoke("no Whisper model is set")
            return
        }
        val projectRoot = File("").absolutePath
        val venvPython = File("$projectRoot/python/.venv/Scripts/python.exe")
        val script = "$projectRoot/python/whisper_streaming_util.py"
        if (!venvPython.exists()) {
            log.error("Python executable not found at: ${venvPython.absolutePath}")
            onFailureListener?.invoke("the Python environment for Whisper is missing")
            return
        }
        val proc = try {
            ProcessBuilder(
                venvPython.absolutePath,
                "-Xutf8",
                "-u",
                script,
                modelPath,
                language,
                stepMillis.toString(),
                minChars.toString(),
                maxUtteranceMillis.toString(),
            ).apply {
                directory(File(projectRoot))
                redirectErrorStream(true)
            }.start()
        } catch (e: Exception) {
            log.error("Failed to start whisper streaming process: ${e.message}", e)
            onFailureListener?.invoke("Whisper streaming could not start: ${e.message}")
            return
        }
        process = proc
        writer = BufferedWriter(OutputStreamWriter(proc.outputStream, Charsets.UTF_8))
        log.debug("whisper streaming started (model=$modelPath, lang=$language, step=$stepMillis, minChars=$minChars, maxMs=$maxUtteranceMillis)")
        readerJob = backgroundThreadScope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val raw = line ?: continue
                    val parsed = try {
                        lineAdapter.fromJson(raw)
                    } catch (e: Exception) {
                        null
                    }
                    if (parsed == null) {
                        log.debug("whisper streaming (raw): $raw")
                        continue
                    }
                    when (parsed.type) {
                        "partial" -> onPartialListener?.invoke(parsed.text ?: "")
                        "final" -> onFinalListener?.invoke(speechOf(parsed.text.orEmpty()))
                        "ready" -> log.debug("whisper streaming ready")
                        "error" -> {
                            log.error("whisper streaming error: ${parsed.text}")
                            onFailureListener?.invoke("Whisper streaming: ${parsed.text}")
                        }
                        else -> log.debug("whisper streaming msg: $raw")
                    }
                }
            } catch (e: Exception) {
                log.warn("whisper streaming reader stopped: ${e.message}")
            }
            if (stoppedProcess !== proc) onFailureListener?.invoke("Whisper streaming stopped on its own")
        }
    }

    override fun feedAudio(pcm: ByteArray) {
        val b64 = Base64.getEncoder().encodeToString(pcm)
        writeLine("""{"cmd":"audio","pcm":"$b64"}""")
    }

    override fun flush() = writeLine("""{"cmd":"flush"}""")

    override fun reset() = writeLine("""{"cmd":"reset"}""")

    override fun stop() {
        val proc = process ?: return
        stoppedProcess = proc
        synchronized(writeLock) {
            try {
                writer?.apply {
                    write("""{"cmd":"exit"}""")
                    newLine()
                    flush()
                }
            } catch (e: Exception) {
                log.debug("whisper streaming exit write failed: ${e.message}")
            }
            writer = null
        }
        readerJob?.cancel()
        readerJob = null
        runCatching {
            if (!proc.waitFor(500, TimeUnit.MILLISECONDS)) proc.destroyForcibly()
        }
        process = null
    }

    private fun writeLine(json: String) {
        if (process?.isAlive != true) return
        synchronized(writeLock) {
            val w = writer ?: return
            try {
                w.write(json)
                w.newLine()
                w.flush()
            } catch (e: Exception) {
                log.debug("whisper streaming write skipped (stream closing): ${e.message}")
            }
        }
    }
}
