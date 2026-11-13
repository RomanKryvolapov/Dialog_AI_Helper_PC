package utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import models.domain.TranslationMethod

/**
 * The translation method the dialog uses now. A dialog starts on method 1; any failure of the method in use —
 * its recogniser or its model — hands every later phrase to the next method of the chain at once, until the
 * dialog is stopped and started again or the chain or keys change. The last method has nowhere to go: it stays.
 */
class ActiveMethod {

    /**
     * [index] in the chain; [left]: why each method the dialog left was left, by [TranslationMethod.key]; [dialog]
     * counts the starts, so a failure from a dialog that has ended moves nothing in the next.
     */
    data class State(val index: Int = 0, val left: Map<String, String> = emptyMap(), val dialog: Long = 0)

    private val lock = Any()
    private val state = MutableStateFlow(State())

    val flow: StateFlow<State> = state

    /** The method in use, from [chain] as it is now. */
    fun current(chain: List<TranslationMethod>): TranslationMethod =
        chain[state.value.index.coerceIn(0, chain.lastIndex)]

    /** Method 1 again: a new dialog, or a chain or keys that changed. */
    fun reset(): Unit = synchronized(lock) { state.value = State(dialog = state.value.dialog + 1) }

    /**
     * [method] failed for [reason] in [dialog]. When it is the method in use, the next one takes over; a late failure
     * of a method the dialog already left, or one from an earlier dialog, changes nothing. True when it switched.
     */
    fun failed(
        chain: List<TranslationMethod>,
        method: TranslationMethod,
        reason: String,
        dialog: Long = state.value.dialog,
    ): Boolean = synchronized(lock) {
        val now = state.value
        if (dialog != now.dialog) return false
        val index = now.index.coerceIn(0, chain.lastIndex)
        if (chain[index].key != method.key) return false
        val left = now.left + (method.key to reason)
        val next = if (index + 1 < chain.size) index + 1 else index
        state.value = State(next, left, now.dialog)
        next != index
    }
}
