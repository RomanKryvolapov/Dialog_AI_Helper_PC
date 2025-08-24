package di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import tabs.MessagesTab
import tabs.PromptTab
import tabs.SettingsTab

val uiModule = module {

    singleOf(::MessagesTab)
    singleOf(::SettingsTab)
    singleOf(::PromptTab)

}