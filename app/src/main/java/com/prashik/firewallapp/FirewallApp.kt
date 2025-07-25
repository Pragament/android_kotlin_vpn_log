package com.prashik.firewallapp

import android.app.Application
import com.prashik.firewallapp.di.DatastoreModule
import com.prashik.firewallapp.di.RoomModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.defaultModule
import org.koin.ksp.generated.module

class FirewallApp: Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FirewallApp)
            modules(defaultModule, DatastoreModule().module, RoomModule().module)
        }
    }
}