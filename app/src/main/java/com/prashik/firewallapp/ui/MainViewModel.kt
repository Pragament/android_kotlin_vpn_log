package com.prashik.firewallapp.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prashik.firewallapp.data.local.dao.BlockLogDao
import com.prashik.firewallapp.data.local.modal.BlockLogEntity
import com.prashik.firewallapp.domain.repository.BlockedAppPreferenceRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class MainViewModel(
    private val blockedAppPreferenceRepo: BlockedAppPreferenceRepo,
    private val blockLogDao: BlockLogDao
) : ViewModel() {

    private val _allBlockLogs = MutableStateFlow<List<BlockLogEntity>>(emptyList())
    val allBlackLogs = _allBlockLogs.asStateFlow()

    val switchState = MutableStateFlow(false)

    private val _installedApps = MutableStateFlow<List<ApplicationInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    private val _isAppListLoading = MutableStateFlow(false)
    val isAppListLoading = _isAppListLoading.asStateFlow()

    fun onSwitchChange(newSwitchState: Boolean) {
        switchState.value = newSwitchState
    }

    fun addBlockedApp(packageName: String) {
        viewModelScope.launch {
            try {
                blockedAppPreferenceRepo.addBlockedApp(packageName)
            } catch (e: Exception) {
                Log.e("Blocked App", "Failed to add $packageName", e)
            }
        }
    }

    fun removeBlockedApp(packageName: String) {
        viewModelScope.launch {
            try {
                blockedAppPreferenceRepo.removeBlockedApp(packageName)
            } catch (e: Exception) {
                Log.e("Blocked App", "Failed to remove $packageName", e)
            }
        }
    }

    val blockedApps = blockedAppPreferenceRepo.getBlockedApps
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptySet()
        )

    fun getBlockLogs() {
        viewModelScope.launch {
            try {
                blockLogDao.getAllLogs().collect {
                    _allBlockLogs.value = it
                }
            } catch (e: Exception) {
                Log.e("Room", "Error: ${e.message}")
            }
        }
    }

    fun loadInstalledApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAppListLoading.value = true
            delay(1000)
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName }
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .sortedBy { it.loadLabel(pm).toString() }
            _installedApps.value = apps
            _isAppListLoading.value = false
        }
    }
}