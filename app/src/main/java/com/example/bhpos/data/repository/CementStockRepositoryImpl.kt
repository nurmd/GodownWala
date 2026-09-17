package com.example.bhpos.data.repository

import com.example.bhpos.domain.model.CementProduct
import com.example.bhpos.domain.model.DashboardTelemetry
import com.example.bhpos.domain.model.Party
import com.example.bhpos.domain.model.StockTransaction
import com.example.bhpos.domain.model.StockTransactionItem
import com.example.bhpos.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CementStockRepositoryImpl : CementStockRepository {

    private val mutex = Mutex()

    private val initialProducts = listOf(
        CementProduct(
            id = "ultratech_ppc",
            name = "UltraTech Super PPC",
            grade = "PPC 50kg",
            weightPerBagKg = 50.0,
            defaultRatePerBag = 380.0,
            bayLocation = "Bay A1-A4",
            currentStockBags = 6200,
            batchNo = "UT-24-OCT-03"
        ),
        CementProduct(
            id = "ambuja_opc",
            name = "Ambuja OPC 53 Grade",
            grade = "OPC 53G",
            weightPerBagKg = 50.0,
            defaultRatePerBag = 410.0,
            bayLocation = "Bay B1-B2",
            currentStockBags = 4150,
            batchNo = "AM-24-OCT-11"
        ),
        CementProduct(
            id = "acc_suraksha",
            name = "ACC Suraksha Power",
            grade = "PPC 50kg",
            weightPerBagKg = 50.0,
            defaultRatePerBag = 395.0,
            bayLocation = "Bay B3-B4",
            currentStockBags = 3250,
            batchNo = "AC-24-OCT-09"
        ),
        CementProduct(
            id = "jk_white",
            name = "JK White Cement Max",
            grade = "Specialty 50kg",
            weightPerBagKg = 50.0,
            defaultRatePerBag = 580.0,
            bayLocation = "Bay C1",
            currentStockBags = 1250,
            batchNo = "JK-24-SEP-28"
        ),
        CementProduct(
            id = "shree_ultra",
            name = "Shree Ultra Jung Rodhak",
            grade = "PPC 50kg",
            weightPerBagKg = 50.0,
            defaultRatePerBag = 375.0,
            bayLocation = "Bay C2",
            currentStockBags = 1800,
            batchNo = "SC-24-OCT-01"
        )
    )

    private val initialParties = listOf(
        Party(
            id = "p1",
            name = "Vanguard Infra Projects Pvt Ltd",
            gstin = "27AABCV8934L1Z2",
            phone = "+91 98234-11029",
            defaultDestination = "Metro Pillar 142 Site, North Corridor",
            accountNo = "CR-8820"
        ),
        Party(
            id = "p2",
            name = "Sharma Infra Projects Ltd.",
            gstin = "27AABCS1234F1Z1",
            phone = "+91 98220-44102",
            defaultDestination = "Ring Road Flyover Pier 12",
            accountNo = "SH-901"
        ),
        Party(
            id = "p3",
            name = "Apex Builders & Developers",
            gstin = "27AABCA5678K1Z3",
            phone = "+91 98221-55099",
            defaultDestination = "Sector 18 Commercial Complex",
            accountNo = "AP-412"
        ),
        Party(
            id = "p4",
            name = "Direct Walk-in Contractor",
            gstin = "UNREGISTERED",
            phone = "+91 99000-00000",
            defaultDestination = "Depot Gate Cash Sale",
            accountNo = "CASH/UPI"
        )
    )

    private val initialTransactions = mutableListOf(
        StockTransaction(
            id = "tx_1",
            slipNo = "GP-9482",
            type = TransactionType.OUTWARD,
            timestamp = System.currentTimeMillis() - 3600_000 * 2,
            partyName = "Sharma Infra Projects Ltd.",
            vehicleNo = "MH-12-QZ-4891",
            driverName = "Rameshwar",
            driverPhone = "+91 98234-11029",
            challanNo = "DC-2024-8841",
            ewbNo = "8921-4402-9912",
            destinationSite = "Ring Road Flyover Pier 12",
            totalBags = 200,
            totalMetricTons = 10.0,
            totalAmount = 76000.0,
            items = listOf(
                StockTransactionItem(
                    productId = "ultratech_ppc",
                    productName = "UltraTech Super PPC",
                    quantityBags = 200,
                    metricTons = 10.0,
                    ratePerBag = 380.0,
                    batchNo = "UT-24-OCT-03",
                    bayLocation = "Bay A1-A4"
                )
            )
        ),
        StockTransaction(
            id = "tx_0",
            slipNo = "GRN-2024-110",
            type = TransactionType.INWARD,
            timestamp = System.currentTimeMillis() - 3600_000 * 5,
            partyName = "UltraTech Cement Works (Factory)",
            vehicleNo = "MH-04-AB-7721",
            driverName = "Balwinder Singh",
            driverPhone = "+91 98111-22334",
            challanNo = "FAC-INV-9901",
            ewbNo = "8833-2211-5544",
            destinationSite = "Godown #4 Bay A1",
            totalBags = 1200,
            totalMetricTons = 60.0,
            totalAmount = 456000.0,
            items = listOf(
                StockTransactionItem(
                    productId = "ultratech_ppc",
                    productName = "UltraTech Super PPC",
                    quantityBags = 1200,
                    metricTons = 60.0,
                    ratePerBag = 380.0,
                    batchNo = "UT-24-OCT-03",
                    bayLocation = "Bay A1-A4"
                )
            )
        )
    )

    private val _products = MutableStateFlow(initialProducts)
    private val _transactions = MutableStateFlow(initialTransactions.toList())

    override fun getProductsFlow(): Flow<List<CementProduct>> = _products.asStateFlow()
    override fun getCurrentProducts(): List<CementProduct> = _products.value

    override fun getParties(): List<Party> = initialParties

    override fun getTransactionsFlow(): Flow<List<StockTransaction>> = _transactions.asStateFlow()
    override fun getCurrentTransactions(): List<StockTransaction> = _transactions.value

    override fun getDashboardTelemetry(): Flow<DashboardTelemetry> {
        return _products.map { products ->
            val totalBags = products.sumOf { it.currentStockBags }
            val totalMt = (totalBags * 50.0) / 1000.0

            DashboardTelemetry(
                totalStoredBags = totalBags,
                totalMetricTons = totalMt,
                inwardDayBags = 1200,
                inwardDayMt = 60.0,
                dispatchedBags = 850,
                dispatchedMt = 42.5,
                pendingSlipsCount = 1,
                netTallyBags = 350
            )
        }
    }

    override suspend fun getTransactionBySlipNo(slipNo: String): StockTransaction? {
        mutex.withLock {
            return _transactions.value.find { it.slipNo.equals(slipNo, ignoreCase = true) }
        }
    }

    override suspend fun getLastSlip(): StockTransaction? {
        mutex.withLock {
            return _transactions.value.firstOrNull()
        }
    }

    override suspend fun recordDispatch(transaction: StockTransaction): Result<StockTransaction> {
        mutex.withLock {
            val currentList = _products.value.toMutableList()

            // Validate inventory sufficiency
            for (item in transaction.items) {
                val index = currentList.indexOfFirst { it.id == item.productId }
                if (index == -1) {
                    return Result.failure(IllegalArgumentException("Product ${item.productName} not found"))
                }
                val product = currentList[index]
                if (product.currentStockBags < item.quantityBags) {
                    return Result.failure(
                        IllegalStateException("Insufficient stock for ${product.name}. Available: ${product.currentStockBags} bags, Requested: ${item.quantityBags} bags")
                    )
                }
            }

            // Deduct stock
            for (item in transaction.items) {
                val index = currentList.indexOfFirst { it.id == item.productId }
                val product = currentList[index]
                currentList[index] = product.copy(
                    currentStockBags = product.currentStockBags - item.quantityBags
                )
            }

            _products.value = currentList
            val updatedTx = _transactions.value.toMutableList()
            updatedTx.add(0, transaction)
            _transactions.value = updatedTx

            return Result.success(transaction)
        }
    }

    override suspend fun recordStockIn(
        productId: String,
        bags: Int,
        batchNo: String,
        bayLocation: String
    ): Result<Unit> {
        if (bags <= 0) {
            return Result.failure(IllegalArgumentException("Stock-in quantity must be positive"))
        }

        mutex.withLock {
            val currentList = _products.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == productId }
            if (index == -1) {
                return Result.failure(IllegalArgumentException("Product ID $productId not found"))
            }

            val product = currentList[index]
            currentList[index] = product.copy(
                currentStockBags = product.currentStockBags + bags,
                batchNo = if (batchNo.isNotBlank()) batchNo else product.batchNo,
                bayLocation = if (bayLocation.isNotBlank()) bayLocation else product.bayLocation
            )
            _products.value = currentList

            val newSlipNo = "GRN-${System.currentTimeMillis() % 100000}"
            val newTx = StockTransaction(
                id = "tx_${System.currentTimeMillis()}",
                slipNo = newSlipNo,
                type = TransactionType.INWARD,
                timestamp = System.currentTimeMillis(),
                partyName = "${product.name} Factory Delivery",
                vehicleNo = "FACTORY-UNLOAD",
                driverName = "Depot Inward",
                driverPhone = "-",
                challanNo = "INW-$newSlipNo",
                ewbNo = "-",
                destinationSite = "Godown #4 - ${product.bayLocation}",
                totalBags = bags,
                totalMetricTons = (bags * 50.0) / 1000.0,
                totalAmount = bags * product.defaultRatePerBag,
                items = listOf(
                    StockTransactionItem(
                        productId = product.id,
                        productName = product.name,
                        quantityBags = bags,
                        metricTons = (bags * 50.0) / 1000.0,
                        ratePerBag = product.defaultRatePerBag,
                        batchNo = product.batchNo,
                        bayLocation = product.bayLocation
                    )
                )
            )

            val updatedTx = _transactions.value.toMutableList()
            updatedTx.add(0, newTx)
            _transactions.value = updatedTx

            return Result.success(Unit)
        }
    }

    override suspend fun updateProduct(product: CementProduct): Result<Unit> {
        mutex.withLock {
            val currentList = _products.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == product.id }
            if (index == -1) {
                return Result.failure(IllegalArgumentException("Product ID ${product.id} not found"))
            }
            currentList[index] = product
            _products.value = currentList
            return Result.success(Unit)
        }
    }

    override suspend fun addProduct(product: CementProduct): Result<Unit> {
        mutex.withLock {
            val currentList = _products.value.toMutableList()
            if (currentList.any { it.id == product.id }) {
                return Result.failure(IllegalArgumentException("Product ID ${product.id} already exists"))
            }
            currentList.add(product)
            _products.value = currentList
            return Result.success(Unit)
        }
    }

    companion object {
        val instance: CementStockRepository by lazy { CementStockRepositoryImpl() }
    }
}
