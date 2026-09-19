package com.example.bhpos.data.repository

import com.example.bhpos.domain.model.Product
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

    private val initialProducts = emptyList<Product>()
    private val _products = MutableStateFlow(initialProducts)
    private val _transactions = MutableStateFlow(emptyList<StockTransaction>())

    override fun setProducts(products: List<Product>) { _products.value = products }
    override fun getProductsFlow(): Flow<List<Product>> = _products.asStateFlow()
    override fun getCurrentProducts(): List<Product> = _products.value

    override fun setTransactions(txs: List<StockTransaction>) { _transactions.value = txs }
    private var _parties = emptyList<Party>()
    override fun getParties(): List<Party> = _parties
    override fun setParties(parties: List<Party>) { _parties = parties }

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

    override suspend fun updateProduct(product: Product): Result<Unit> {
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

    override suspend fun addProduct(product: Product): Result<Unit> {
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
