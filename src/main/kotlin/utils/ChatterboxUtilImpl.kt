package utils

import defaultThreadScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ChatterboxUtilImpl: ChatterboxUtil {

    private val log = LoggerFactory.getLogger("ChatterboxUtil")

    override fun speak(message: String, language: String) {
        defaultThreadScope.launch {
            log.debug("Speaking: $message")
            try {
                val projectRoot = File("").absolutePath
                val venvPythonPath = "$projectRoot/python/.venv/Scripts/python.exe"
                val scriptPath = "$projectRoot/python/chatterbox_util.py"

                val pythonExecutable = File(venvPythonPath)

                if (!pythonExecutable.exists()) {
                    log.error("Python executable not found at: $venvPythonPath")
                    return@launch
                }

                val command = listOf(
                    pythonExecutable.absolutePath,
                    "-Xutf8",
                    scriptPath,
                    message,
                    language,
                )

                val processBuilder = ProcessBuilder(command).apply {
                    directory(File(projectRoot))
                    redirectErrorStream(true)
                }

                log.debug("Command: ${command.joinToString(" ")}")

                val process = processBuilder.start()

                val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    log.error(line)
                    output.appendLine(line)
                }

                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    log.error("Chatterbox process exited with code $exitCode")
                }

            } catch (e: Exception) {
                log.error("Exception during Chatterbox execution", e)
            }
        }
    }

}