package com.example.bhpos.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object PosTerminal : Screen("pos")
    object NewDispatch : Screen("dispatch")
    object StockLedger : Screen("ledger")
    object ThermalPreview : Screen("preview/{slipNo}") {
        fun createRoute(slipNo: String): String = "preview/$slipNo"
    }
}
