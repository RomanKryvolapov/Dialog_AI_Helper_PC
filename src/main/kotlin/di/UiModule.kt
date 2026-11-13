package di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import ui.ConsoleTab
import ui.MessagesTab
import ui.MainPromptTab
import ui.QuestionsPromptTab
import ui.QuestionsTab
import utils.QuestionsDisplay
import ui.MethodChainSelector
import ui.ModelParametersPane
import ui.SettingsTab
import ui.VoicePromptTab

val uiModule = module {

    singleOf(::MessagesTab)
    singleOf(::QuestionsTab) bind QuestionsDisplay::class
    singleOf(::QuestionsPromptTab)
    singleOf(::SettingsTab)
    singleOf(::MainPromptTab)
    singleOf(::VoicePromptTab)
    singleOf(::ModelParametersPane)
    singleOf(::ConsoleTab)
    singleOf(::MethodChainSelector)

}
