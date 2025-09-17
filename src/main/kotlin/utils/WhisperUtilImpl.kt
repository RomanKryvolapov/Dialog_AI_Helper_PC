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

    override fun runFasterWhisper(wavFile: File, language: String) {
        defaultThreadScope.launch {
            log.debug("run whisper")
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
                    language
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
                    val text = result.toString().replace(Regex("""(\.\s*){2,}"""), "").normalizeAndRemoveEmptyLines()
                    log.debug("text: $text")
                    if (text.isNotBlank()) {
                        onResultListener?.invoke(text)
                    }
                }
                wavFile.delete()
            } catch (e: Exception) {
                log.error("Exception during whisper execution", e)
            }
        }
    }

}