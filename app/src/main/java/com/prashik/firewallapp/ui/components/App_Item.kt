package com.prashik.firewallapp.ui.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.prashik.firewallapp.FirewallVpnService
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@Composable
fun App_Item(
    isBlocked: Boolean,
    iconColor: Color,
    packageManager: PackageManager,
    appInfo: ApplicationInfo,
    onBlockIconClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = appInfo.loadIcon(packageManager)
    val appName = appInfo.loadLabel(packageManager).toString()
    val packageName = appInfo.packageName

    val isRunning = FirewallVpnService.isRunning.load()
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFf5f5f5)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = "App icon",
                contentScale = ContentScale.Inside,
                modifier = Modifier
                    .clip(CircleShape)
                    .size(56.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = appName,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray,
                    fontSize = 17.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = packageName,
                    fontSize = 15.sp,
                    color = Color.Gray,
                    maxLines = 2
                )
            }
            IconButton(
                onClick = {
                    if (isRunning) {
                        Toast.makeText(
                            context,
                            "Stop the VPN Service",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onBlockIconClicked(appName)
                    }
                }
            ) {
                Icon(
                    imageVector = if (isBlocked) Icons.Default.Close else Icons.Default.Block,
                    contentDescription = "Block",
                    tint = iconColor
                )
            }
        }
    }
}