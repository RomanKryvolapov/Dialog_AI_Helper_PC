package utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import models.domain.ModelPrompt

/**
 * The last request of each kind exactly as it was sent, and the conversation summary as it stands, for the
 * prompt screens to show what the model really gets. Kept in memory only; Clear dialog empties the summary.
 */
class LastRequests {

    data class Sent(val model: String, val prompt: ModelPrompt, val atMillis: Long = System.currentTimeMillis())

    private val textState = MutableStateFlow<Sent?>(null)
    private val voiceState = MutableStateFlow<Sent?>(null)
    private val summaryRequestState = MutableStateFlow<Sent?>(null)
    private val questionsState = MutableStateFlow<Sent?>(null)
    private val summaryState = MutableStateFlow("")

    val text: StateFlow<Sent?> = textState
    val voice: StateFlow<Sent?> = voiceState
    val summaryRequest: StateFlow<Sent?> = summaryRequestState
    val questions: StateFlow<Sent?> = questionsState
    val summary: StateFlow<String> = summaryState

    fun textSent(sent: Sent) { textState.value = sent }
    fun voiceSent(sent: Sent) { voiceState.value = sent }
    fun summarySent(sent: Sent) { summaryRequestState.value = sent }
    fun questionsSent(sent: Sent) { questionsState.value = sent }
    fun summaryIs(summary: String) { summaryState.value = summary }
}
