package com.prashik.firewallapp.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prashik.firewallapp.R
import com.prashik.firewallapp.ui.MainViewModel
import com.prashik.firewallapp.ui.components.App_Item
import com.prashik.firewallapp.ui.components.Search_Text_Field

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Select_Apps(
    onNavBackClicked: () -> Unit,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val blockedAppList = viewModel.blockedApps.collectAsStateWithLifecycle()
    val isLoading = viewModel.isAppListLoading.collectAsStateWithLifecycle()
    var allApps = viewModel.installedApps.collectAsStateWithLifecycle()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }


    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps(context)
    }
    val filteredApps = if (searchQuery.isBlank()) {
        allApps.value
    } else {
        allApps.value.filter { applicationInfo ->
            applicationInfo.loadLabel(packageManager).contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            if (!isSearching) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Select apps to block",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { onNavBackClicked() }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Nav back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isSearching = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorResource(R.color.home_header_container),
                        titleContentColor = colorResource(R.color.white),
                        navigationIconContentColor = colorResource(R.color.white),
                        actionIconContentColor = colorResource(R.color.white)
                    )
                )
            } else {
                Search_Text_Field(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onIsSearchingChange = { isSearching = it }
                )
            }
        }
    ) { paddingValues ->
        if (isLoading.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.White)
            ) {
                items(filteredApps) { applicationInfo ->
                    App_Item(
                        isBlocked = false,
                        appInfo = applicationInfo,
                        packageManager = packageManager,
                        onBlockIconClicked = { appName ->
                            if (applicationInfo.packageName !in blockedAppList.value) {
                                viewModel.addBlockedApp(applicationInfo.packageName)
                                Toast.makeText(context, "$appName is blocked", Toast.LENGTH_SHORT)
                                    .show()
                            } else {
                                Toast.makeText(context, "Already blocked", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                        iconColor = if (applicationInfo.packageName in blockedAppList.value) Color.Red else Color.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}