package com.example.recordamed.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.recordamed.ui.screens.add_edit.AddEditMedicationScreen
import com.example.recordamed.ui.screens.detail.MedicationDetailScreen
import com.example.recordamed.ui.screens.history.HistoryScreen
import com.example.recordamed.ui.screens.home.HomeScreen
import com.example.recordamed.ui.screens.settings.SettingsScreen

object Destinations {
    const val HOME = "home"
    const val ADD_EDIT = "add_edit?medicationId={medicationId}"
    const val DETAIL = "detail/{medicationId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun addEditRoute(medicationId: Long = 0L) = "add_edit?medicationId=$medicationId"
    fun detailRoute(medicationId: Long) = "detail/$medicationId"
}

@Composable
fun RecordaMedNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME
    ) {
        composable(Destinations.HOME) {
            HomeScreen(
                onNavigateToAddMedication = {
                    navController.navigate(Destinations.addEditRoute(0L))
                },
                onNavigateToDetail = { medId ->
                    navController.navigate(Destinations.detailRoute(medId))
                },
                onNavigateToHistory = {
                    navController.navigate(Destinations.HISTORY)
                },
                onNavigateToSettings = {
                    navController.navigate(Destinations.SETTINGS)
                }
            )
        }

        composable(
            route = Destinations.ADD_EDIT,
            arguments = listOf(
                navArgument("medicationId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getLong("medicationId") ?: 0L
            AddEditMedicationScreen(
                medicationId = if (medicationId > 0L) medicationId else null,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Destinations.DETAIL,
            arguments = listOf(
                navArgument("medicationId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getLong("medicationId") ?: 0L
            MedicationDetailScreen(
                medicationId = medicationId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToEdit = { medId ->
                    navController.navigate(Destinations.addEditRoute(medId))
                }
            )
        }

        composable(Destinations.HISTORY) {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
