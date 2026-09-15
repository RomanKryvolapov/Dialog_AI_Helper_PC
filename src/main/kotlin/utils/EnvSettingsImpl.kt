package utils

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the `.env` file from the working directory once, at first use, and answers lookups from it.
 * An OS environment variable of the same name wins over the file, which is the usual `.env`
 * contract: the file supplies what the environment has not already set.
 *
 * Values are never logged — only key names — because the file holds API tokens.
 */
class EnvSettingsImpl : EnvSettings {

    companion object {
        private const val ENV_FILE_NAME = ".env"
        private const val EXPORT_PREFIX = "export "
        private const val COMMENT_PREFIX = "#"
        private const val INLINE_COMMENT = " #"
    }

    private val log = LoggerFactory.getLogger("EnvSettings")

    private val fileValues: Map<String, String> by lazy { readEnvFile() }

    // The settings are re-read on every recognized phrase, so a bad value must not flood the log.
    private val warnedKeys = ConcurrentHashMap.newKeySet<String>()

    override fun getString(key: String): String? {
        val fromProcessEnvironment = System.getenv(key)
        if (!fromProcessEnvironment.isNullOrBlank()) return fromProcessEnvironment.trim()
        return fileValues[key]?.takeIf { it.isNotBlank() }
    }

    override fun getInt(key: String, min: Int): Int? {
        val raw = getString(key) ?: return null
        val value = raw.toIntOrNull()
        if (value == null) {
            warnOnce(key, "$key is not a whole number, ignored")
            return null
        }
        if (value < min) {
            warnOnce(key, "$key must be $min or more, ignored")
            return null
        }
        return value
    }

    override fun getLong(key: String, min: Long): Long? {
        val raw = getString(key) ?: return null
        val value = raw.toLongOrNull()
        if (value == null) {
            warnOnce(key, "$key is not a whole number, ignored")
            return null
        }
        if (value < min) {
            warnOnce(key, "$key must be $min or more, ignored")
            return null
        }
        return value
    }

    override fun getBoolean(key: String): Boolean? {
        val raw = getString(key) ?: return null
        return when (raw.lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> {
                warnOnce(key, "$key is not true/false, ignored")
                null
            }
        }
    }

    override fun <T : Enum<T>> getEnum(key: String, values: Array<T>): T? {
        val raw = getString(key) ?: return null
        val value = values.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        if (value == null) {
            warnOnce(key, "$key must be one of ${values.joinToString { it.name }}, ignored")
        }
        return value
    }

    /** Reports a key that `.env` supplies but a saved setting overrides, so it is not a silent no-op. */
    override fun noteOverriddenByStoredValue(key: String) {
        warnOnce("overridden:$key", "$key is set in .env, but the app has its own saved value for it, which wins")
    }

    private fun warnOnce(key: String, message: String) {
        if (warnedKeys.add(key)) log.warn(message)
    }

    private fun readEnvFile(): Map<String, String> {
        val file = File(File("").absolutePath, ENV_FILE_NAME)
        if (!file.isFile) {
            log.info("No ${file.absolutePath}, settings come from preferences and built-in defaults only")
            return emptyMap()
        }
        val values = mutableMapOf<String, String>()
        try {
            file.readLines(Charsets.UTF_8).forEachIndexed { index, rawLine ->
                val line = rawLine.trim().removePrefix("﻿")
                if (line.isEmpty() || line.startsWith(COMMENT_PREFIX)) return@forEachIndexed
                val assignment = line.removePrefix(EXPORT_PREFIX).trim()
                val separator = assignment.indexOf('=')
                if (separator <= 0) {
                    log.warn("${file.name} line ${index + 1} is not KEY=VALUE, skipped")
                    return@forEachIndexed
                }
                val key = assignment.substring(0, separator).trim()
                values[key] = unquote(key, assignment.substring(separator + 1).trim())
            }
        } catch (e: Exception) {
            log.error("Could not read ${file.absolutePath}: ${e.message}", e)
            return emptyMap()
        }
        log.info("Loaded ${values.size} value(s) from ${file.absolutePath}: ${values.keys.sorted()}")
        return values
    }

    /**
     * A quoted value is whatever sits between the quotes, so anything after the closing quote is a
     * comment. An unquoted value runs to a trailing ` # comment`, if there is one.
     */
    private fun unquote(key: String, raw: String): String {
        val quote = raw.firstOrNull()
        if (quote == '"' || quote == '\'') {
            val closing = raw.indexOf(quote, startIndex = 1)
            if (closing > 0) return raw.substring(1, closing)
            warnOnce(key, "$key opens with $quote but never closes it — taken literally, quote included")
        }
        val commentStart = raw.indexOf(INLINE_COMMENT)
        return if (commentStart >= 0) raw.substring(0, commentStart).trim() else raw
    }

}
