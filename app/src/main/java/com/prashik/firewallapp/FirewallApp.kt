package com.prashik.firewallapp

import android.app.Application

class FirewallApp: Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: FirewallApp
    }
}