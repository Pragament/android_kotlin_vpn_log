package com.prashik.firewallapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.prashik.firewallapp.ui.screen.Main_Screen
import com.prashik.firewallapp.ui.theme.FirewallAppTheme


class MainActivity : ComponentActivity() {
    companion object {
        init {
            System.loadLibrary("firewallapp")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FirewallAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Main_Screen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}