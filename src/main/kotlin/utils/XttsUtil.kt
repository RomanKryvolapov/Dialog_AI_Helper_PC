package utils

import java.io.File

interface XttsUtil {

    fun speak(
        message: String,
        voiceSample: File?,
    )

}