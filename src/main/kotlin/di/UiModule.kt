package di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import ui.ConsoleTab
import ui.MessagesTab
import ui.PromptTab
import ui.SettingsTab

val uiModule = module {

    singleOf(::MessagesTab)
    singleOf(::SettingsTab)
    singleOf(::PromptTab)
    singleOf(::ConsoleTab)

}