package com.prashik.firewallapp.ui.bottomBar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

val bottomBarList = listOf<BottomBarScreen>(
    BottomBarScreen.BlockedApps,
    BottomBarScreen.TrafficLogs
)

@Serializable
sealed class BottomBarScreen(
    @Contextual val selectedIcon: ImageVector,
    @Contextual val unSelectedIcon: ImageVector,
    val title: String
) : NavKey {

    @Serializable
    data object BlockedApps : BottomBarScreen(
        selectedIcon = Icons.Default.Widgets,
        unSelectedIcon = Icons.Outlined.Widgets,
        title = "Blocked Apps"
    )

    @Serializable
    data object TrafficLogs : BottomBarScreen(
        selectedIcon = Icons.Default.AppBlocking,
        unSelectedIcon = Icons.Outlined.AppBlocking,
        title = "Traffic Logs"
    )
}

val BottomBarSaver = Saver<BottomBarScreen, String>(
    save = { it::class.simpleName ?: "Unknown" },
    restore = {
        when (it) {
            BottomBarScreen.BlockedApps::class.simpleName -> BottomBarScreen.BlockedApps
            BottomBarScreen.TrafficLogs::class.simpleName -> BottomBarScreen.TrafficLogs
            else -> BottomBarScreen.BlockedApps
        }
    }
)