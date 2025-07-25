package com.prashik.firewallapp.ui.bottomBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.prashik.firewallapp.ui.MainViewModel
import com.prashik.firewallapp.ui.screen.Select_Apps
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier
) {
    val newBackStack = rememberNavBackStack<Screen>(Screen.NestedGraph)

    val viewModel: MainViewModel = koinViewModel()

    val switchState = viewModel.switchState.collectAsStateWithLifecycle()
    LaunchedEffect(switchState.value) {
        viewModel.getBlockLogs()
    }
    val firewallTrafficLogs = viewModel.allBlackLogs.collectAsStateWithLifecycle()

    NavDisplay(
        backStack = newBackStack,
        onBack = { newBackStack.removeLastOrNull() },
        modifier = modifier,
        entryProvider = entryProvider {
            entry<Screen.SelectApps> {
                Select_Apps(
                    onNavBackClicked = {
                        newBackStack.removeLastOrNull()
                    },
                    viewModel = viewModel
                )
            }
            entry<Screen.NestedGraph> {
                Nested_Graph(
                    onFabClicked = { newBackStack.add(Screen.SelectApps) },
                    viewModel = viewModel,
                    switchState = switchState.value,
                    onSwitchStateChange = { switchState ->
                        viewModel.onSwitchChange(switchState)
                    },
                    firewallTrafficLogs = firewallTrafficLogs.value
                )
            }
        }
    )
}