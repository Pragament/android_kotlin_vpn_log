package com.prashik.firewallapp.domain.repository

import kotlinx.coroutines.flow.Flow

interface BlockedAppPreferenceRepo {
    suspend fun addBlockedApp(packageName: String)
    suspend fun removeBlockedApp(packageName: String)
    val getBlockedApps: Flow<Set<String>>
}