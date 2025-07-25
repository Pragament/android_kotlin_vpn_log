package com.prashik.firewallapp.di

import android.content.Context
import com.prashik.firewallapp.data.repository.BlockedAppRepositoryImpl
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class DatastoreModule {
    @Single
    fun provideBlockedAppPreferenceRepo(applicationContext: Context): BlockedAppRepositoryImpl {
        return BlockedAppRepositoryImpl(applicationContext)
    }
}