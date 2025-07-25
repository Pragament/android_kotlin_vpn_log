package com.prashik.firewallapp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prashik.firewallapp.data.local.modal.BlockLogEntity
import com.prashik.firewallapp.ui.components.TrafficLog_Item

@Composable
fun Main_Screen(
    switchState: Boolean,
    searchQuery: String,
    firewallTrafficLogs: List<BlockLogEntity>,
    modifier: Modifier = Modifier
) {
    val filteredLogs = if (searchQuery.isBlank()) {
        firewallTrafficLogs
    } else {
        firewallTrafficLogs.filter { trafficResponse ->
            trafficResponse.appName.contains(searchQuery, ignoreCase = true)
        }
    }

    if (switchState) {
        if (firewallTrafficLogs.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "VPN is running...",
                    fontSize = 22.sp,
                    color = Color.Black
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxWidth()
            ) {
                if (switchState) {
                    items(items = filteredLogs) { trafficLog ->
                        TrafficLog_Item(
                            blockLogEntity = trafficLog,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
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
                text = "VPN is off",
                fontSize = 22.sp,
                color = Color.Black
            )
        }
    }
}