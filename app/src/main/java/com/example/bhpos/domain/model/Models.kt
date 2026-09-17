package com.example.bhpos.domain.model

enum class TransactionType {
    INWARD,
    OUTWARD,
    DAMAGED
}

data class CementProduct(
    val id: String,
    val name: String,
    val grade: String, // e.g. PPC, OPC 53G, White
    val weightPerBagKg: Double = 50.0,
    val defaultRatePerBag: Double,
    val bayLocation: String, // e.g. Bay A1-A4
    val currentStockBags: Int,
    val batchNo: String = "UT-24-OCT-03",
    val imageUrl: String? = null,
    val isActive: Boolean = true
) {
    val stockMetricTons: Double
        get() = (currentStockBags * weightPerBagKg) / 1000.0
}

data class Party(
    val id: String,
    val name: String,
    val gstin: String,
    val phone: String,
    val defaultDestination: String,
    val accountNo: String
)

data class StockTransactionItem(
    val productId: String,
    val productName: String,
    val quantityBags: Int,
    val metricTons: Double,
    val ratePerBag: Double,
    val batchNo: String,
    val bayLocation: String
)

data class StockTransaction(
    val id: String,
    val slipNo: String,
    val type: TransactionType,
    val timestamp: Long,
    val partyName: String,
    val vehicleNo: String,
    val driverName: String,
    val driverPhone: String,
    val challanNo: String,
    val ewbNo: String,
    val destinationSite: String,
    val totalBags: Int,
    val totalMetricTons: Double,
    val totalAmount: Double,
    val items: List<StockTransactionItem>
)

data class DashboardTelemetry(
    val totalStoredBags: Int,
    val totalMetricTons: Double,
    val inwardDayBags: Int,
    val inwardDayMt: Double,
    val dispatchedBags: Int,
    val dispatchedMt: Double,
    val pendingSlipsCount: Int,
    val netTallyBags: Int
)
