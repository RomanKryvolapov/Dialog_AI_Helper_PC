package utils

import defaultThreadScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class XttsUtilImpl : XttsUtil {

    private val log = LoggerFactory.getLogger("XttsUtil")

    override fun speak(message: String, voiceSample: File?) {
        defaultThreadScope.launch {
            log.debug("Speaking: $message")
            try {
                val projectRoot = File("").absolutePath
                val scriptPath = "$projectRoot/python/xtts_util.py"
                val pythonPath = "C:/Users/Roman/AppData/Local/Programs/Python/Python311/python.exe"

                val command = listOf(
                    pythonPath,
                    "-Xutf8",
                    scriptPath,
                    message,
                    voiceSample?.absolutePath ?: "$projectRoot/cache/chunk_0.wav"
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
                    log.debug(line)
                    output.appendLine(line)
                }

                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    log.error("XTTS process exited with code $exitCode")
                }

            } catch (e: Exception) {
                log.error("Exception during XTTS execution", e)
            }
        }
    }

}
