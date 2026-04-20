package di

import mappers.domain.ApplicationInfoMapper
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val mappersModule = module {

    singleOf(::ApplicationInfoMapper)

}
