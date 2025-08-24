package di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import repository.CloudRepository
import repository.CloudRepositoryImpl
import repository.LocalNetworkRepository
import repository.LocalNetworkRepositoryImpl
import repository.PreferencesRepository
import repository.PreferencesRepositoryImpl

val repositoryModule = module {

    singleOf(::CloudRepositoryImpl) bind CloudRepository::class
    singleOf(::LocalNetworkRepositoryImpl) bind LocalNetworkRepository::class
    singleOf(::PreferencesRepositoryImpl) bind PreferencesRepository::class

}