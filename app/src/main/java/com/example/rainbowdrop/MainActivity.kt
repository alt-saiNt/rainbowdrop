package com.example.rainbowdrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.rainbowdrop.engine.FilterType
import com.example.rainbowdrop.engine.Tool
import com.example.rainbowdrop.navigation.Navigator
import com.example.rainbowdrop.navigation.Route
import com.example.rainbowdrop.navigation.rememberNavigationState
import com.example.rainbowdrop.navigation.toEntries
import com.example.rainbowdrop.ui.screens.EditorScreen
import com.example.rainbowdrop.ui.screens.FilterSelectionScreen
import com.example.rainbowdrop.ui.screens.HomeScreen
import com.example.rainbowdrop.ui.screens.MethodSelectionScreen
import com.example.rainbowdrop.ui.theme.RainbowDropTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RainbowDropTheme {
                val navigationState = rememberNavigationState(
                    startRoute = Route.Home,
                    topLevelRoutes = setOf(Route.Home)
                )
                val navigator = remember { Navigator(navigationState) }

                val entryProvider = remember {
                    entryProvider<NavKey> {
                        entry<Route.Home> {
                            HomeScreen(
                                onImageSelected = { uri ->
                                    navigator.navigate(Route.FilterSelection(uri))
                                }
                            )
                        }
                        entry<Route.FilterSelection> { key ->
                            FilterSelectionScreen(
                                imageUri = key.imageUri,
                                onFilterSelected = { filter ->
                                    navigator.navigate(Route.MethodSelection(key.imageUri, filter.name))
                                },
                                onBack = { navigator.goBack() }
                            )
                        }
                        entry<Route.MethodSelection> { key ->
                            MethodSelectionScreen(
                                imageUri = key.imageUri,
                                filterType = FilterType.valueOf(key.filterType),
                                onMethodSelected = { tool, isMystery ->
                                    navigator.navigate(
                                        Route.Editor(
                                            imageUri = key.imageUri,
                                            filterType = key.filterType,
                                            tool = tool.name,
                                            isMysteryMode = isMystery
                                        )
                                    )
                                },
                                onBack = { navigator.goBack() }
                            )
                        }
                        entry<Route.Editor> { key ->
                            EditorScreen(
                                imageUri = key.imageUri,
                                filterType = FilterType.valueOf(key.filterType),
                                tool = Tool.valueOf(key.tool),
                                isMysteryMode = key.isMysteryMode,
                                onBack = { navigator.goBack() }
                            )
                        }
                    }
                }

                NavDisplay(
                    entries = navigationState.toEntries(entryProvider),
                    onBack = { navigator.goBack() }
                )
            }
        }
    }
}
