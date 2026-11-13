package utils

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import org.slf4j.LoggerFactory
import java.io.File

class SherpaOnnxRecognizerImpl : SherpaOnnxRecognizer {

    private val log = LoggerFactory.getLogger("SherpaOnnxRecognizer")
    private val lock = Any()

    override var onPartialListener: ((String) -> Unit)? = null
    override var onFinalListener: ((String) -> Unit)? = null
    override var onFailureListener: ((String) -> Unit)? = null

    @Volatile
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var lastPartial = ""
    private var splitMaxWords = 0
    private var maxHangMillis = 0L
    private var utteranceStartMillis = 0L

    override fun isRunning(): Boolean = recognizer != null

    override fun start(modelDir: String, numThreads: Int, decodingMethod: String, maxActivePaths: Int, splitMaxWords: Int, maxHangMillis: Long) {
        stop()
        this.splitMaxWords = splitMaxWords
        this.maxHangMillis = maxHangMillis
        if (modelDir.isBlank()) {
            log.error("Sherpa: model path is empty")
            onFailureListener?.invoke("no Sherpa-ONNX model is set")
            return
        }
        val dir = File(modelDir)
        if (!dir.isDirectory) {
            log.error("Sherpa: model dir not found: $modelDir")
            onFailureListener?.invoke("the Sherpa-ONNX model folder is missing")
            return
        }
        val onnxFiles = dir.listFiles { f -> f.isFile && f.name.endsWith(".onnx", ignoreCase = true) }
            ?.toList().orEmpty()
        fun pick(keyword: String): String? {
            val matches = onnxFiles.filter { it.name.contains(keyword, ignoreCase = true) }
            val preferred = matches.firstOrNull { it.name.contains("int8", ignoreCase = true) } ?: matches.firstOrNull()
            return preferred?.absolutePath
        }
        val encoder = pick("encoder")
        val decoder = pick("decoder")
        val joiner = pick("joiner")
        val tokens = File(dir, "tokens.txt").takeIf { it.isFile }?.absolutePath
        if (encoder == null || decoder == null || joiner == null || tokens == null) {
            log.error("Sherpa: missing model files in $modelDir (encoder=$encoder decoder=$decoder joiner=$joiner tokens=$tokens)")
            onFailureListener?.invoke("the Sherpa-ONNX model folder lacks its model files")
            return
        }
        try {
            val config = OnlineRecognizerConfig.builder()
                .setFeatureConfig(
                    FeatureConfig.builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setFeatureDim(80)
                        .build()
                )
                .setOnlineModelConfig(
                    OnlineModelConfig.builder()
                        .setTransducer(
                            OnlineTransducerModelConfig.builder()
                                .setEncoder(encoder)
                                .setDecoder(decoder)
                                .setJoiner(joiner)
                                .build()
                        )
                        .setTokens(tokens)
                        .setNumThreads(numThreads.coerceAtLeast(1))
                        .setProvider("cpu")
                        .build()
                )
                .setEnableEndpoint(true)
                .setDecodingMethod(decodingMethod)
                .setMaxActivePaths(maxActivePaths.coerceAtLeast(1))
                .build()
            val rec = OnlineRecognizer(config)
            synchronized(lock) {
                recognizer = rec
                stream = rec.createStream()
                lastPartial = ""
                utteranceStartMillis = 0L
            }
            log.debug("Sherpa started (encoder=${File(encoder).name}, threads=$numThreads, decoding=$decodingMethod, splitMaxWords=$splitMaxWords, maxHangMillis=$maxHangMillis)")
        } catch (e: Throwable) {
            log.error("Sherpa: failed to init: ${e.message}", e)
            stop()
            onFailureListener?.invoke("Sherpa-ONNX could not start: ${e.message}")
        }
    }

    override fun feedAudio(pcm: ByteArray) {
        synchronized(lock) {
            val rec = recognizer ?: return
            val st = stream ?: return
            try {
                st.acceptWaveform(pcmToFloat(pcm), SAMPLE_RATE)
                while (rec.isReady(st)) rec.decode(st)
                val text = rec.getResult(st).text.trim()
                if (text.isNotBlank() && utteranceStartMillis == 0L) {
                    utteranceStartMillis = System.currentTimeMillis()
                }
                val wordCount = if (text.isEmpty()) 0 else text.split(WHITESPACE).size
                val lengthSplit = splitMaxWords > 0 && wordCount >= splitMaxWords
                val hangSplit = maxHangMillis > 0 && utteranceStartMillis > 0L &&
                    System.currentTimeMillis() - utteranceStartMillis >= maxHangMillis && text.isNotBlank()
                if (rec.isEndpoint(st) || lengthSplit || hangSplit) {
                    if (text.isNotBlank()) onFinalListener?.invoke(prettify(text))
                    rec.reset(st)
                    lastPartial = ""
                    utteranceStartMillis = 0L
                } else if (text.isNotBlank() && text != lastPartial) {
                    lastPartial = text
                    onPartialListener?.invoke(prettify(text))
                }
            } catch (e: Throwable) {
                log.error("Sherpa: decode error: ${e.message}", e)
                onFailureListener?.invoke("Sherpa-ONNX could not decode: ${e.message}")
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            runCatching { stream?.release() }
            runCatching { recognizer?.release() }
            stream = null
            recognizer = null
            lastPartial = ""
            utteranceStartMillis = 0L
        }
    }

    /** Lowercase the all-caps model output and capitalize the first letter. */
    private fun prettify(text: String): String =
        text.lowercase().replaceFirstChar { it.uppercase() }

    /** 16-bit signed little-endian PCM -> normalized float [-1, 1]. */
    private fun pcmToFloat(pcm: ByteArray): FloatArray {
        val count = pcm.size / 2
        val out = FloatArray(count)
        var j = 0
        for (i in 0 until count) {
            val lo = pcm[j].toInt() and 0xFF
            val hi = pcm[j + 1].toInt()
            out[i] = ((hi shl 8) or lo) / 32768.0f
            j += 2
        }
        return out
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private val WHITESPACE = Regex("\\s+")
    }
}
