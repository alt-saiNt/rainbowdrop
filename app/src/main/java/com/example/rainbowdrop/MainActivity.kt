package com.example.rainbowdrop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.rainbowdrop.navigation.Navigator
import com.example.rainbowdrop.navigation.Route
import com.example.rainbowdrop.navigation.rememberNavigationState
import com.example.rainbowdrop.navigation.toEntries
import com.example.rainbowdrop.ui.screens.EditorScreen
import com.example.rainbowdrop.ui.screens.HomeScreen
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
                                    navigator.navigate(Route.Editor(uri))
                                }
                            )
                        }
                        entry<Route.Editor> { key ->
                            EditorScreen(
                                imageUri = key.imageUri,
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
