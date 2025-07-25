package com.prashik.firewallapp.ui.bottomBar

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.prashik.firewallapp.FirewallVpnService
import com.prashik.firewallapp.R
import com.prashik.firewallapp.data.local.modal.BlockLogEntity
import com.prashik.firewallapp.ui.MainViewModel
import com.prashik.firewallapp.ui.components.Search_Text_Field
import com.prashik.firewallapp.ui.screen.Blocked_Apps
import com.prashik.firewallapp.ui.screen.Main_Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Nested_Graph(
    onFabClicked: () -> Unit,
    viewModel: MainViewModel,
    switchState: Boolean,
    onSwitchStateChange: (Boolean) -> Unit,
    firewallTrafficLogs: List<BlockLogEntity>,
    modifier: Modifier = Modifier
) {
    val backStack = rememberNavBackStack(BottomBarScreen.BlockedApps)

    var currentBottomBarScreen by rememberSaveable(stateSaver = BottomBarSaver) {
        mutableStateOf(
            BottomBarScreen.BlockedApps
        )
    }

    var appIsSearching by rememberSaveable { mutableStateOf(false) }
    var logIsSearching by rememberSaveable { mutableStateOf(false) }
    var appSearchQuery by rememberSaveable { mutableStateOf("") }
    var logSearchQuery by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        when (it.resultCode) {
            Activity.RESULT_OK -> {
                val intent = Intent(context, FirewallVpnService::class.java).apply {
                    action = "START_VPN_SERVICE"
                }

                ContextCompat.startForegroundService(context, intent)
                onSwitchStateChange(true)
            }

            Activity.RESULT_CANCELED -> {
                onSwitchStateChange(false)
            }
        }
    }

    Scaffold(
        topBar = {
            if (!(appIsSearching || logIsSearching)) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Firewall App",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = if (switchState) colorResource(R.color.home_header_on_text) else Color.Black
                        )
                    },
                    actions = {
                        Switch(
                            checked = switchState,
                            onCheckedChange = {
                                if (!switchState) {
                                    val vpnIntent = VpnService.prepare(context)
                                    if (vpnIntent != null) {
                                        vpnPermissionLauncher.launch(vpnIntent)
                                    } else {
                                        ContextCompat.startForegroundService(
                                            context,
                                            Intent(
                                                context,
                                                FirewallVpnService::class.java
                                            ).apply { action = "START_VPN_SERVICE" }
                                        )
                                        onSwitchStateChange(true)
                                    }
                                } else {
                                    val stopIntent = Intent(
                                        context,
                                        FirewallVpnService::class.java
                                    ).apply { action = "STOP_VPN_SERVICE" }

                                    context.startService(stopIntent)
                                    onSwitchStateChange(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colorResource(R.color.home_header_on_text),
                                checkedTrackColor = colorResource(R.color.home_header_on_text).copy(
                                    alpha = 0.3f
                                ),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.Black.copy(alpha = 0.4f),
                                checkedBorderColor = Color.Transparent,
                                uncheckedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                        IconButton(
                            onClick = {
                                when (currentBottomBarScreen) {
                                    is BottomBarScreen.BlockedApps -> appIsSearching = true
                                    is BottomBarScreen.TrafficLogs -> logIsSearching = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorResource(R.color.home_header_container)

                    )
                )
            } else {
                when (currentBottomBarScreen) {
                    is BottomBarScreen.BlockedApps -> {
                        Search_Text_Field(
                            searchQuery = appSearchQuery,
                            onSearchQueryChange = { appSearchQuery = it },
                            onIsSearchingChange = { appIsSearching = it }
                        )
                    }

                    is BottomBarScreen.TrafficLogs -> {
                        Search_Text_Field(
                            searchQuery = logSearchQuery,
                            onSearchQueryChange = { logSearchQuery = it },
                            onIsSearchingChange = { logIsSearching = it }
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                bottomBarList.forEach { destination ->
                    NavigationBarItem(
                        selected = currentBottomBarScreen == destination,
                        icon = {
                            Icon(
                                imageVector = if (currentBottomBarScreen == destination) destination.selectedIcon else destination.unSelectedIcon,
                                contentDescription = "Blocked Apps",
                                tint = if (currentBottomBarScreen == destination) colorResource(R.color.home_header_container) else Color.Unspecified
                            )
                        },
                        label = {
                            Text(
                                text = destination.title
                            )
                        },
                        onClick = {
                            if (backStack.lastOrNull() != destination) {
                                backStack.removeLastOrNull()
                                backStack.add(destination)
                                currentBottomBarScreen = destination
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onFabClicked() },
                shape = CircleShape,
                containerColor = colorResource(R.color.home_header_on_text),
                contentColor = colorResource(R.color.white),
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        NavDisplay(
            backStack = backStack,
            modifier = modifier.padding(paddingValues),
            entryProvider = entryProvider {
                entry<BottomBarScreen.BlockedApps> {
                    Blocked_Apps(
                        searchQuery = appSearchQuery,
                        viewModel = viewModel
                    )
                }
                entry<BottomBarScreen.TrafficLogs> {
                    Main_Screen(
                        switchState = switchState,
                        searchQuery = logSearchQuery,
                        firewallTrafficLogs = firewallTrafficLogs
                    )
                }
            }
        )
    }
}

