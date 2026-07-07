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

    @Volatile
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private val writeLock = Any()

    data class StreamLine(val type: String?, val text: String?)

    override fun isRunning(): Boolean = process?.isAlive == true

    override fun start(modelPath: String, language: String, stepMillis: Long, minChars: Int, maxUtteranceMillis: Long) {
        stop()
        if (modelPath.isBlank()) {
            log.error("Whisper streaming: model path is empty")
            return
        }
        val projectRoot = File("").absolutePath
        val venvPython = File("$projectRoot/python/.venv/Scripts/python.exe")
        val script = "$projectRoot/python/whisper_streaming_util.py"
        if (!venvPython.exists()) {
            log.error("Python executable not found at: ${venvPython.absolutePath}")
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
                        "final" -> onFinalListener?.invoke(parsed.text ?: "")
                        "ready" -> log.debug("whisper streaming ready")
                        "error" -> log.error("whisper streaming error: ${parsed.text}")
                        else -> log.debug("whisper streaming msg: $raw")
                    }
                }
            } catch (e: Exception) {
                log.warn("whisper streaming reader stopped: ${e.message}")
            }
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
