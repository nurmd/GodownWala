package com.example.bhpos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bhpos.data.repository.CementStockRepositoryImpl
import com.example.bhpos.ui.dashboard.DashboardScreen
import com.example.bhpos.ui.dispatch.NewDispatchScreen
import com.example.bhpos.ui.ledger.StockLedgerScreen
import com.example.bhpos.ui.navigation.Screen
import com.example.bhpos.ui.pos.PosTerminalScreen
import com.example.bhpos.ui.preview.ThermalPrintPreviewScreen
import com.example.bhpos.ui.theme.BHPOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BHPOSTheme {
                CementTrackApp()
            }
        }
    }
}

@Composable
fun CementTrackApp() {
    val navController = rememberNavController()
    val repository = CementStockRepositoryImpl.instance

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                repository = repository,
                onNavigateToPos = { navController.navigate(Screen.PosTerminal.route) },
                onNavigateToDispatch = { navController.navigate(Screen.NewDispatch.route) },
                onNavigateToLedger = { navController.navigate(Screen.StockLedger.route) },
                onNavigateToPrintSlip = { slipNo ->
                    navController.navigate(Screen.ThermalPreview.createRoute(slipNo))
                }
            )
        }

        composable(Screen.PosTerminal.route) {
            PosTerminalScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onSlipCreated = { slipNo ->
                    navController.navigate(Screen.ThermalPreview.createRoute(slipNo)) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(Screen.NewDispatch.route) {
            NewDispatchScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onSlipIssued = { slipNo ->
                    navController.navigate(Screen.ThermalPreview.createRoute(slipNo)) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(Screen.StockLedger.route) {
            StockLedgerScreen(
                repository = repository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrintSlip = { slipNo ->
                    navController.navigate(Screen.ThermalPreview.createRoute(slipNo))
                }
            )
        }

        composable(
            route = Screen.ThermalPreview.route,
            arguments = listOf(navArgument("slipNo") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val slipNo = backStackEntry.arguments?.getString("slipNo")
            ThermalPrintPreviewScreen(
                repository = repository,
                slipNo = slipNo,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
