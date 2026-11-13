package utils

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import models.domain.SpeechmaticsConfig
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** The fields of a Speechmatics realtime message the app reads (docs.speechmatics.com/rt-api-ref). */
internal data class SpeechmaticsMessage(
    val message: String? = null,
    val metadata: SpeechmaticsMetadata? = null,
    val results: List<SpeechmaticsResult>? = null,
    val type: String? = null,
    val reason: String? = null,
)

internal data class SpeechmaticsMetadata(val transcript: String? = null)

internal data class SpeechmaticsResult(
    val type: String? = null,
    @Json(name = "is_eos") val isEos: Boolean? = null,
    val alternatives: List<SpeechmaticsAlternative>? = null,
)

internal data class SpeechmaticsAlternative(val content: String? = null, val speaker: String? = null)

class SpeechmaticsRecognizerImpl(moshi: Moshi) : SpeechmaticsRecognizer {

    private companion object {
        /** A sentence this long is a phrase of its own, pause or not, like Whisper streaming's minimum. */
        const val MIN_PHRASE_CHARS = 20
        /** Seconds the service may wait before a word is final (0.7–4): short, for live translation. */
        const val MAX_DELAY_SECONDS = 1.0
        val SENTENCE_END = setOf(".", "?", "!")
        /** The label Speechmatics gives a word it cannot attribute. */
        const val UNKNOWN_SPEAKER = "UU"
    }

    private val log = LoggerFactory.getLogger("Speechmatics")
    private val messages = moshi.adapter(SpeechmaticsMessage::class.java)
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override var onPartialListener: ((String) -> Unit)? = null
    override var onFinalListener: ((text: String, speaker: String?) -> Unit)? = null
    override var onFailureListener: ((String) -> Unit)? = null

    private val lock = Any()
    private var socket: WebSocket? = null
    /** A WebSocket allows one send at a time: each send waits for the one before it. */
    private var sending: CompletableFuture<WebSocket>? = null
    /** Audio may flow once the service has said RecognitionStarted. */
    private var recognizing = false
    /** Bumped by every start and stop, so a connection that completes late knows it is no longer wanted. */
    private var generation = 0

    // The phrase being settled: its final words so far, and how many of them each speaker said.
    private val phrase = StringBuilder()
    private val speakers = HashMap<String, Int>()

    override fun isRunning(): Boolean = synchronized(lock) { socket != null }

    override fun start(apiKey: String, language: String, config: SpeechmaticsConfig) {
        stop()
        val mine = synchronized(lock) { ++generation }
        http.newWebSocketBuilder()
            .header("Authorization", "Bearer $apiKey")
            .buildAsync(URI.create(config.endpoint), Listener(mine))
            .whenComplete { ws, error ->
                if (error != null) {
                    if (synchronized(lock) { generation == mine }) fail("Speechmatics could not connect: ${(error.cause ?: error).message}")
                    return@whenComplete
                }
                val wanted = synchronized(lock) {
                    if (generation != mine) return@synchronized false
                    socket = ws
                    sending = CompletableFuture.completedFuture(ws)
                    true
                }
                if (!wanted) {
                    ws.abort()
                    return@whenComplete
                }
                log.debug("Speechmatics connected to {} (model {}, speaker labels {})", config.endpoint, config.model, config.speakerLabels)
                send { it.sendText(startRecognition(language, config), true) }
            }
    }

    override fun feedAudio(pcm: ByteArray) {
        if (!synchronized(lock) { recognizing }) return
        val audio = ByteBuffer.wrap(pcm.copyOf())
        send { it.sendBinary(audio, true) }
    }

    override fun flush() {
        if (synchronized(lock) { recognizing }) send { it.sendText("""{"message":"ForceEndOfUtterance"}""", true) }
    }

    override fun stop() {
        val ws = synchronized(lock) {
            generation += 1
            recognizing = false
            sending = null
            phrase.setLength(0)
            speakers.clear()
            socket.also { socket = null }
        } ?: return
        // Abort, not EndOfStream: what the service would still send back is no longer wanted.
        ws.abort()
    }

    private fun send(action: (WebSocket) -> CompletableFuture<WebSocket>) {
        synchronized(lock) {
            val before = sending ?: return
            sending = before.thenCompose(action)
        }
    }

    private fun startRecognition(language: String, config: SpeechmaticsConfig): String =
        """{"message":"StartRecognition","audio_format":{"type":"raw","encoding":"pcm_s16le","sample_rate":16000},""" +
            """"transcription_config":{"language":"$language","enable_partials":true,"max_delay":$MAX_DELAY_SECONDS,""" +
            """"model":"${config.model}","diarization":"${if (config.speakerLabels) "speaker" else "none"}",""" +
            """"conversation_config":{"end_of_utterance_silence_trigger":0.5}}}"""

    private fun handle(json: String) {
        val msg = try {
            messages.fromJson(json)
        } catch (e: Exception) {
            log.debug("Speechmatics sent something unreadable: {}", json.take(200))
            null
        } ?: return
        when (msg.message) {
            "RecognitionStarted" -> {
                synchronized(lock) { recognizing = true }
                log.debug("Speechmatics recognition started")
            }
            "AddPartialTranscript" -> {
                val shown = (synchronized(lock) { phrase.toString() } + " " + msg.metadata?.transcript.orEmpty()).trim()
                if (shown.isNotEmpty()) onPartialListener?.invoke(shown)
            }
            "AddTranscript" -> {
                // One message can end a sentence and start the next: the phrase ends right at the sentence end.
                msg.results.orEmpty().forEach { result ->
                    val alternative = result.alternatives?.firstOrNull() ?: return@forEach
                    val content = alternative.content ?: return@forEach
                    val sentenceEnded = synchronized(lock) {
                        if (result.type == "punctuation") {
                            phrase.append(content)
                            (content in SENTENCE_END || result.isEos == true) && phrase.length >= MIN_PHRASE_CHARS
                        } else {
                            if (phrase.isNotEmpty()) phrase.append(' ')
                            phrase.append(content)
                            alternative.speaker?.takeIf { it != UNKNOWN_SPEAKER }?.let { speakers.merge(it, 1, Int::plus) }
                            false
                        }
                    }
                    if (sentenceEnded) finishPhrase()
                }
            }
            "EndOfUtterance" -> finishPhrase()
            "Error" -> fail("Speechmatics: ${msg.type}: ${msg.reason}")
            "Warning" -> log.warn("Speechmatics warning {}: {}", msg.type, msg.reason)
            "Info" -> log.debug("Speechmatics info {}: {}", msg.type, msg.reason)
        }
    }

    private fun finishPhrase() {
        val (text, speaker) = synchronized(lock) {
            val text = phrase.toString().trim()
            val speaker = speakers.maxByOrNull { it.value }?.key
            phrase.setLength(0)
            speakers.clear()
            text to speaker
        }
        if (text.isNotEmpty()) onFinalListener?.invoke(text, speaker)
    }

    /** The session ended without [stop]: say so once, and drop the socket. */
    private fun fail(reason: String) {
        val ws = synchronized(lock) {
            // Whatever this session still reports (an Error is followed by a close) is no longer news.
            generation += 1
            recognizing = false
            sending = null
            socket.also { socket = null }
        }
        ws?.abort()
        log.warn(reason)
        onFailureListener?.invoke(reason)
    }

    private inner class Listener(private val generation: Int) : WebSocket.Listener {
        private val text = StringBuilder()

        private fun current(): Boolean = synchronized(lock) { this@SpeechmaticsRecognizerImpl.generation == generation }

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)
            if (last) {
                val json = text.toString()
                text.setLength(0)
                if (current()) handle(json)
            }
            webSocket.request(1)
            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            if (current()) fail("Speechmatics closed the session ($statusCode${reason.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""})")
            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (current()) fail("Speechmatics connection failed: ${error.message}")
        }
    }
}
