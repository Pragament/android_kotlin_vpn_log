package com.prashik.firewallapp.ui.screen

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prashik.firewallapp.R
import com.prashik.firewallapp.data.local.modal.SelectedAppData
import com.prashik.firewallapp.ui.MainViewModel
import com.prashik.firewallapp.ui.components.App_Item

@Composable
fun Blocked_Apps(
    searchQuery: String,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val blockedApps = viewModel.blockedApps.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val packageManger = context.packageManager

    var isDeleting by rememberSaveable { mutableStateOf(false) }
    var selectedApp by rememberSaveable { mutableStateOf<SelectedAppData?>(null) }

    val filteredBlockedApp = if (searchQuery.isBlank()) {
        blockedApps.value.toList().reversed()
    } else {
        blockedApps.value.filter { packageName ->
            val appName = try {
                val appInfo = packageManger.getApplicationInfo(packageName, 0)
                packageManger.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                ""
            }
            appName.contains(searchQuery, true)
        }
    }
    if (isDeleting) {
        AlertDialog(
            onDismissRequest = {
                isDeleting = false
                selectedApp = null
            },
            title = {
                Text(
                    text = "Unblock ${selectedApp?.appName}"
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isDeleting = false
                        selectedApp = null
                    }
                ) {
                    Text(
                        text = "Cancel"
                    )
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        isDeleting = false
                        selectedApp?.packageName?.let { viewModel.removeBlockedApp(it) }
                        selectedApp = null
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colorResource(R.color.home_header_on_text),
                        contentColor = colorResource(R.color.white)
                    )
                ) {
                    Text(
                        text = "Unblock"
                    )
                }
            }
        )
    }

    if (filteredBlockedApp.isNotEmpty()) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            items(filteredBlockedApp.reversed()) { packageName ->
                val appInfo = try {
                    packageManger.getApplicationInfo(packageName, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }

                if (appInfo != null) {
                    App_Item(
                        isBlocked = true,
                        packageManager = packageManger,
                        appInfo = appInfo,
                        onBlockIconClicked = {
                            isDeleting = true
                            selectedApp = SelectedAppData(packageName, it)
                        },
                        iconColor = Color.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Click + to block the app",
                fontSize = 22.sp,
                color = Color.Black
            )
        }
    }
}