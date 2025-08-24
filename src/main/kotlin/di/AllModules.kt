package di

import org.koin.dsl.module

val allModules = module {
    includes(
        uiModule,
        mappersModule,
        repositoryModule,
        utilsModule,
    )
}