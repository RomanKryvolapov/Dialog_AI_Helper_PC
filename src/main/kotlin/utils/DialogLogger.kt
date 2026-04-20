package utils

interface DialogLogger {

    fun startSession()

    fun logQuestion(text: String)

    fun logAnswer(text: String)

}
