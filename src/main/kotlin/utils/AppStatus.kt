package utils

import kotlinx.coroutines.flow.MutableStateFlow

object AppStatus {
    val statusFlow = MutableStateFlow("")
}
