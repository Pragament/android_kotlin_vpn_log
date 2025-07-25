package com.prashik.firewallapp.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.prashik.firewallapp.domain.repository.BlockedAppPreferenceRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

val Context.dataStore by preferencesDataStore(name = "blockedApps")

    object PreferenceKeys {
        val BLOCKED_SET = stringSetPreferencesKey("blocked_packages")
    }
@Single
class BlockedAppRepositoryImpl(
    private val context: Context
) : BlockedAppPreferenceRepo {



    override suspend fun addBlockedApp(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PreferenceKeys.BLOCKED_SET] ?: emptySet()
            prefs[PreferenceKeys.BLOCKED_SET] = current + packageName
        }
    }

    override suspend fun removeBlockedApp(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PreferenceKeys.BLOCKED_SET] ?: emptySet()
            prefs[PreferenceKeys.BLOCKED_SET] = current - packageName
        }
    }

    override val getBlockedApps: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[PreferenceKeys.BLOCKED_SET] ?: emptySet() }
}