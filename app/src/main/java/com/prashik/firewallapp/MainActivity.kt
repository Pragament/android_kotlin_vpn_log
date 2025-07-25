package com.prashik.firewallapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.prashik.firewallapp.ui.bottomBar.AppNavigation
import com.prashik.firewallapp.ui.theme.FirewallAppTheme


class MainActivity : ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("firewallapp")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                Color.Black.toArgb()
            )
        )
        setContent {
            FirewallAppTheme {
                AppNavigation()
            }
        }
    }
}