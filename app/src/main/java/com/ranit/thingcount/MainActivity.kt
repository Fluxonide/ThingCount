package com.ranit.thingcount

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ranit.thingcount.ui.screens.CounterDetailScreen
import com.ranit.thingcount.ui.screens.HomeScreen
import com.ranit.thingcount.ui.screens.SettingsSyncScreen
import com.ranit.thingcount.ui.theme.ThingCountTheme
import com.ranit.thingcount.ui.viewmodel.CounterViewModel
import com.ranit.thingcount.ui.viewmodel.CounterViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: CounterViewModel by viewModels {
        val app = application as ThingCountApp
        CounterViewModelFactory(app.repository, app.driveSyncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ThingCountTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToDetail = { counterId ->
                                    navController.navigate("detail/$counterId")
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }

                        composable(
                            route = "detail/{counterId}",
                            arguments = listOf(navArgument("counterId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val counterId = backStackEntry.arguments?.getString("counterId") ?: ""
                            CounterDetailScreen(
                                counterId = counterId,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsSyncScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
