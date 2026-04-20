package di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import repository.CloudRepository
import repository.CloudRepositoryImpl
import repository.PreferencesRepository
import repository.PreferencesRepositoryImpl

val repositoryModule = module {

    singleOf(::CloudRepositoryImpl) bind CloudRepository::class
    singleOf(::PreferencesRepositoryImpl) bind PreferencesRepository::class

}
