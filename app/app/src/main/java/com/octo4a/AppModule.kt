package com.octo4a

import com.google.gson.FieldNamingPolicy
import com.octo4a.repository.*
import com.octo4a.viewmodel.InstallationViewModel
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.features.json.*
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        HttpClient(Android) {
            install(JsonFeature) {
                serializer = GsonSerializer {
                    serializeNulls()
                    setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                }
            }
        }
    }

    factory<BootstrapRepository> { BootstrapRepositoryImpl() }
    factory <GithubRepository> { GithubRepositoryImpl(get()) }

    single<OctoPrintHandlerRepository> { OctoPrintHandlerRepositoryImpl(androidContext(), get(), get()) }

    viewModel { InstallationViewModel(get()) }
}