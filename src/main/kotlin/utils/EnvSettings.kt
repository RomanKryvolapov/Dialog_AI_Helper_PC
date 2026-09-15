package utils

/**
 * Fallback values for the application settings, taken from the process environment:
 * an OS environment variable if it is set, otherwise the `.env` file in the working directory.
 */
interface EnvSettings {

    fun getString(key: String): String?

    /** [min] mirrors the lower bound the Settings screen enforces; a smaller value is refused. */
    fun getInt(key: String, min: Int): Int?

    fun getLong(key: String, min: Long): Long?

    fun getBoolean(key: String): Boolean?

    fun <T : Enum<T>> getEnum(key: String, values: Array<T>): T?

    /** Logs, once, that this key is set but a saved setting takes precedence over it. */
    fun noteOverriddenByStoredValue(key: String)

}
