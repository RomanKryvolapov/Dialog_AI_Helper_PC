package utils

import backgroundThreadScope
import defaultThreadScope
import extensions.normalizeAndRemoveEmptyLines
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import repository.PreferencesRepository
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class WhisperUtilImpl(
    private val preferencesRepository: PreferencesRepository
): WhisperUtil {

    private val log = LoggerFactory.getLogger("WhisperUtil")

    private var modelPath = ""

    override var onResultListener: ((String) -> Unit)? = null

    init {
        backgroundThreadScope.launch {
            preferencesRepository.appInfoFlow.collect { appInfo ->
                modelPath = appInfo.whisperModelPath
            }
        }
    }

    override fun runFasterWhisper(wavFile: File) {
        defaultThreadScope.launch {
            log.debug("run whisper")
            try {
                val projectRoot = File("").absolutePath
                val scriptPath = "$projectRoot/python/whisper_util.py"
                val processBuilder = ProcessBuilder(
                    "python",
                    "-Xutf8",
                    scriptPath,
                    wavFile.absolutePath,
                    modelPath
                ).apply {
                    directory(File(projectRoot))
                    redirectErrorStream(true)
                }
                val process = processBuilder.start()
                val output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                val result = StringBuilder()
                var line: String?
                while (output.readLine().also { line = it } != null) {
                    result.appendLine(line)
                }
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    log.error("Python script exited with code $exitCode")
                } else {
                    val text = result.toString().normalizeAndRemoveEmptyLines()
                    log.debug("text: $text")
                    if (text.isNotBlank()) {
                        onResultListener?.invoke(text)
                    }
                }
//                wavFile.delete()
            } catch (e: Exception) {
                log.error("Exception during whisper execution", e)
            }
        }
    }

}