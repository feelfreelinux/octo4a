package com.octo4a

import com.google.gson.FieldNamingPolicy
import com.octo4a.camera.CameraEnumerationRepository
import com.octo4a.repository.*
import com.octo4a.serial.VirtualSerialDriver
import com.octo4a.utils.TLSSocketFactory
import com.octo4a.utils.preferences.MainPreferences
import com.octo4a.viewmodel.InstallationViewModel
import com.octo4a.viewmodel.NetworkStatusViewModel
import com.octo4a.viewmodel.StatusViewModel
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.features.json.*
import org.koin.android.ext.koin.androidApplication
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
            engine {
                sslManager = {
                    it.sslSocketFactory = TLSSocketFactory(androidContext())
                }
            }
        }
    }

    factory { MainPreferences(androidContext()) }
    factory<GithubRepository> { GithubRepositoryImpl(get()) }

    single<BootstrapRepository> { BootstrapRepositoryImpl(get(), get(), androidContext()) }
    single<FIFOEventRepository> { FIFOEventRepositoryImpl(get()) }
    single<VirtualSerialDriver> { VirtualSerialDriver(androidContext(), get(), get(), get()) }
    single<ExtensionsRepository> { ExtensionsRepositoryImpl(androidContext(), get(), get(), get()) }
    single<LoggerRepository> { LoggerRepositoryImpl() }
    single<OctoPrintHandlerRepository> { OctoPrintHandlerRepositoryImpl(androidContext(), get(), get(), get(), get(), get(), get()) }
    single { CameraEnumerationRepository(androidApplication()) }

    viewModel { InstallationViewModel(androidApplication(), get(), get(), get()) }
    viewModel { StatusViewModel(androidApplication(), get(), get()) }
    viewModel { NetworkStatusViewModel(androidApplication(), get()) }
}