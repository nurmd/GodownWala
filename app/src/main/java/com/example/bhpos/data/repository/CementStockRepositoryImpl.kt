package com.example.bhpos.data.repository

import android.content.Context
import com.example.bhpos.data.local.GodownDatabase
import com.example.bhpos.data.local.OutboxEntity
import com.example.bhpos.data.local.PartyEntity
import com.example.bhpos.data.local.ProductEntity
import com.example.bhpos.data.local.TransactionEntity
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class CementStockRepositoryImpl constructor() : CementStockRepository {

    private val mutex = Mutex()

    private var db: GodownDatabase? = null

    private val _products = MutableStateFlow(emptyList<Product>())
    private val _transactions = MutableStateFlow(emptyList<StockTransaction>())
    private var _parties = emptyList<Party>()

    fun init(context: Context) {
        val database = GodownDatabase.get(context)
        db = database
        // Hydrate in-memory state from local persistence
        runCatching {
            runBlocking {
                _products.value = database.productDao().getAll().map { it.toDomain() }
                _transactions.value = database.transactionDao().getAll().map { it.toDomain() }
                _parties = database.partyDao().getAll().map { it.toDomain() }
            }
        }
    }

    override fun setProducts(products: List<Product>) {
        _products.value = products
        val database = db ?: return
        // Write-through persistence (best effort, off the critical path)
        Thread {
            runCatching {
                runBlocking { products.forEach { database.productDao().upsert(it.toEntity()) } }
            }
        }.start()
    }

    override fun getProductsFlow(): Flow<List<Product>> = _products.asStateFlow()
    override fun getCurrentProducts(): List<Product> = _products.value

    override fun setTransactions(txs: List<StockTransaction>) {
        _transactions.value = txs
        val database = db ?: return
        Thread {
            runCatching {
                runBlocking { txs.forEach { database.transactionDao().upsert(it.toEntity()) } }
            }
        }.start()
    }

    override fun setParties(parties: List<Party>) {
        _parties = parties
        val database = db ?: return
        Thread {
            runCatching {
                runBlocking { parties.forEach { database.partyDao().upsert(it.toEntity()) } }
            }
        }.start()
    }

    override fun getParties(): List<Party> = _parties

    override fun getTransactionsFlow(): Flow<List<StockTransaction>> = _transactions.asStateFlow()
    override fun getCurrentTransactions(): List<StockTransaction> = _transactions.value

    override fun getDashboardTelemetry(): Flow<DashboardTelemetry> {
        return _products.map { products ->
            val totalBags = products.sumOf { it.currentStockBags }
            val totalMt = products.sumOf { (it.currentStockBags * it.weightPerBagKg) / 1000.0 }
            DashboardTelemetry(
                totalStoredBags = totalBags,
                totalMetricTons = totalMt,
                inwardDayBags = 0,
                inwardDayMt = 0.0,
                dispatchedBags = 0,
                dispatchedMt = 0.0,
                pendingSlipsCount = 0,
                netTallyBags = 0
            )
        }
    }

    override suspend fun getTransactionBySlipNo(slipNo: String): StockTransaction? {
        mutex.withLock {
            val local = _transactions.value.find { it.slipNo.equals(slipNo, ignoreCase = true) }
            if (local != null) return local
            val database = db ?: return null
            return runCatching { database.transactionDao().getBySlipNo(slipNo)?.toDomain() }.getOrNull()
        }
    }

    override suspend fun getLastSlip(): StockTransaction? {
        mutex.withLock {
            return _transactions.value.maxByOrNull { it.timestamp }
                ?: db?.transactionDao()?.getAll()?.firstOrNull()?.toDomain()
        }
    }

    override suspend fun recordDispatch(transaction: StockTransaction): Result<StockTransaction> {
        mutex.withLock {
            val currentList = _products.value.toMutableList()

            for (item in transaction.items) {
                val index = currentList.indexOfFirst { it.id == item.productId }
                if (index == -1) {
                    return Result.failure(IllegalArgumentException("Product ${item.productId} not found in inventory"))
                }
                val product = currentList[index]
                if (item.quantityBags <= 0) {
                    return Result.failure(IllegalArgumentException("Dispatch quantity must be positive"))
                }
                if (product.currentStockBags < item.quantityBags) {
                    return Result.failure(IllegalArgumentException("Insufficient stock for ${product.name}: requested ${item.quantityBags}, available ${product.currentStockBags}"))
                }
            }

            for (item in transaction.items) {
                val index = currentList.indexOfFirst { it.id == item.productId }
                if (index != -1) {
                    val product = currentList[index]
                    currentList[index] = product.copy(
                        currentStockBags = (product.currentStockBags - item.quantityBags).coerceAtLeast(0)
                    )
                }
            }

            _products.value = currentList
            val updatedTx = _transactions.value.toMutableList()
            updatedTx.add(0, transaction)
            _transactions.value = updatedTx

            val database = db
            if (database != null) {
                runCatching {
                    currentList.forEach { database.productDao().upsert(it.toEntity()) }
                    database.transactionDao().upsert(transaction.toEntity())
                }
            }

            if (db != null) enqueueOutbox("transaction", transaction.id, "upsert", transaction.toJson())

            return Result.success(transaction)
        }
    }

    override suspend fun updateDispatch(transaction: StockTransaction): Result<StockTransaction> {
        mutex.withLock {
            val updatedTx = _transactions.value.toMutableList()
            val txIndex = updatedTx.indexOfFirst { it.slipNo == transaction.slipNo }
            if (txIndex == -1) {
                return Result.failure(Exception("Transaction with slip no ${transaction.slipNo} not found"))
            }
            val existingTx = updatedTx[txIndex]
            val currentProducts = _products.value.toMutableList()

            val affectedProductIds = (existingTx.items.map { it.productId } + transaction.items.map { it.productId }).toSet()
            for (pId in affectedProductIds) {
                val oldQty = existingTx.items.filter { it.productId == pId }.sumOf { it.quantityBags }
                val newQty = transaction.items.filter { it.productId == pId }.sumOf { it.quantityBags }
                val deltaDispatched = newQty - oldQty
                if (deltaDispatched != 0) {
                    val pIndex = currentProducts.indexOfFirst { it.id == pId }
                    if (pIndex != -1) {
                        val prod = currentProducts[pIndex]
                        val newStock = (prod.currentStockBags - deltaDispatched).coerceAtLeast(0)
                        currentProducts[pIndex] = prod.copy(currentStockBags = newStock)
                    }
                }
            }
            _products.value = currentProducts

            updatedTx[txIndex] = transaction
            _transactions.value = updatedTx

            val database = db
            if (database != null) {
                runCatching {
                    currentProducts.forEach { database.productDao().upsert(it.toEntity()) }
                    database.transactionDao().upsert(transaction.toEntity())
                }
            }

            if (db != null) enqueueOutbox("transaction", transaction.id, "upsert", transaction.toJson())

            return Result.success(transaction)
        }
    }

    override suspend fun recordStockIn(
        productId: String,
        bags: Int,
        batchNo: String,
        bayLocation: String
    ): Result<String> {
        if (bags <= 0) {
            return Result.failure(IllegalArgumentException("Stock-in quantity must be positive"))
        }

        mutex.withLock {
            val currentList = _products.value.toMutableList()
            var index = currentList.indexOfFirst { it.id == productId }
            if (index == -1) {
                val adHoc = Product(
                    id = productId,
                    name = "Product $productId",
                    grade = "Standard",
                    weightPerBagKg = 50.0,
                    defaultRatePerBag = 0.0,
                    bayLocation = if (bayLocation.isNotBlank()) bayLocation else "Unassigned",
                    currentStockBags = 0,
                    batchNo = if (batchNo.isNotBlank()) batchNo else "INW"
                )
                currentList.add(adHoc)
                index = currentList.size - 1
            }

            val product = currentList[index]
            currentList[index] = product.copy(
                currentStockBags = product.currentStockBags + bags,
                batchNo = if (batchNo.isNotBlank()) batchNo else product.batchNo,
                bayLocation = if (bayLocation.isNotBlank()) bayLocation else product.bayLocation
            )
            _products.value = currentList

            val updatedProduct = currentList[index]
            val newSlipNo = "GRN-${System.currentTimeMillis()}"
            val newTx = StockTransaction(
                id = "tx_${System.currentTimeMillis()}_${(0..9999).random()}",
                slipNo = newSlipNo,
                type = TransactionType.INWARD,
                timestamp = System.currentTimeMillis(),
                partyName = "${updatedProduct.name} Factory Delivery",
                vehicleNo = "FACTORY-UNLOAD",
                driverName = "Depot Inward",
                driverPhone = "-",
                challanNo = "INW-$newSlipNo",
                ewbNo = "-",
                destinationSite = "Godown #4 - ${updatedProduct.bayLocation}",
                totalBags = bags,
                totalMetricTons = (bags * updatedProduct.weightPerBagKg) / 1000.0,
                totalAmount = bags * updatedProduct.defaultRatePerBag,
                items = listOf(
                    StockTransactionItem(
                        productId = updatedProduct.id,
                        productName = updatedProduct.name,
                        quantityBags = bags,
                        metricTons = (bags * updatedProduct.weightPerBagKg) / 1000.0,
                        ratePerBag = updatedProduct.defaultRatePerBag,
                        batchNo = updatedProduct.batchNo,
                        bayLocation = updatedProduct.bayLocation
                    )
                )
            )

            val updatedTx = _transactions.value.toMutableList()
            updatedTx.add(0, newTx)
            _transactions.value = updatedTx

            val database = db
            if (database != null) {
                runCatching {
                    currentList.forEach { database.productDao().upsert(it.toEntity()) }
                    database.transactionDao().upsert(newTx.toEntity())
                }
            }

            if (db != null) enqueueOutbox("transaction", newTx.id, "upsert", newTx.toJson())

            return Result.success(newSlipNo)
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

            db?.let { database ->
                runCatching { database.productDao().upsert(product.toEntity()) }
            }
            if (db != null) enqueueOutbox("product", product.id, "upsert", product.toJson())

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

            db?.let { database ->
                runCatching { database.productDao().upsert(product.toEntity()) }
            }
            if (db != null) enqueueOutbox("product", product.id, "upsert", product.toJson())

            return Result.success(Unit)
        }
    }

    override suspend fun deleteDispatch(slipNo: String): Result<Unit> {
        mutex.withLock {
            val tx = _transactions.value.find { it.slipNo == slipNo }
                ?: return Result.failure(Exception("Transaction with slip no $slipNo not found"))
            // Restore stock
            val currentProducts = _products.value.toMutableList()
            for (item in tx.items) {
                val idx = currentProducts.indexOfFirst { it.id == item.productId }
                if (idx != -1) {
                    val p = currentProducts[idx]
                    val newStock = if (tx.type == TransactionType.OUTWARD) {
                        p.currentStockBags + item.quantityBags
                    } else {
                        (p.currentStockBags - item.quantityBags).coerceAtLeast(0)
                    }
                    currentProducts[idx] = p.copy(currentStockBags = newStock)
                }
            }
            _products.value = currentProducts
            _transactions.value = _transactions.value.filterNot { it.slipNo == slipNo }

            db?.let { database ->
                runCatching {
                    currentProducts.forEach { database.productDao().upsert(it.toEntity()) }
                    database.transactionDao().deleteBySlipNo(slipNo)
                }
            }
            enqueueOutbox("transaction", tx.id, "delete", "{\"slipNo\":\"$slipNo\"}")

            return Result.success(Unit)
        }
    }

    private fun enqueueOutbox(entityType: String, entityId: String, operation: String, payloadJson: String) {
        val database = db ?: return
        Thread {
            runCatching {
                runBlocking {
                    database.outboxDao().enqueue(OutboxEntity(
                        entityType = entityType,
                        entityId = entityId,
                        operation = operation,
                        payloadJson = payloadJson
                    ))
                }
            }
        }.start()
    }

    fun flushOutbox() {
        val database = db ?: return
        Thread {
            runCatching {
                runBlocking { val items = database.outboxDao().getAll() }
                // Actual cloud push is handled by the JS sync layer; here we just
                // keep the queue durable. Callers can read the queue via getOutbox().
            }
        }.start()
    }

    fun getOutbox(): List<OutboxEntity> {
        val database = db ?: return emptyList()
        return runCatching { runBlocking { database.outboxDao().getAll() } }.getOrDefault(emptyList())
    }

    fun clearOutboxRow(rowId: Long) {
        db?.let { database -> runCatching { runBlocking { database.outboxDao().delete(rowId) } } }
    }

    companion object {
        val instance: CementStockRepositoryImpl by lazy { CementStockRepositoryImpl() }
    }
}

// --- Mapping helpers ---

private fun ProductEntity.toDomain(): Product = Product(
    id = id,
    name = name,
    grade = grade,
    weightPerBagKg = weightPerBagKg,
    defaultRatePerBag = defaultRatePerBag,
    bayLocation = bayLocation,
    currentStockBags = currentStockBags,
    batchNo = batchNo,
    imageUrl = imageUrl,
    isActive = isActive,
    unit = unit
)

private fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id,
    name = name,
    grade = grade,
    unit = unit,
    weightPerBagKg = weightPerBagKg,
    defaultRatePerBag = defaultRatePerBag,
    bayLocation = bayLocation,
    currentStockBags = currentStockBags,
    batchNo = batchNo,
    imageUrl = imageUrl,
    isActive = isActive
)

private fun PartyEntity.toDomain(): Party = Party(
    id = id,
    name = name,
    gstin = gstin,
    phone = phone,
    defaultDestination = defaultDestination,
    accountNo = accountNo
)

private fun Party.toEntity(): PartyEntity = PartyEntity(
    id = id,
    name = name,
    gstin = gstin,
    phone = phone,
    defaultDestination = defaultDestination,
    accountNo = accountNo
)

private fun TransactionEntity.toDomain(): StockTransaction {
    val itemsArray = org.json.JSONArray(itemsJson)
    val items = mutableListOf<StockTransactionItem>()
    for (i in 0 until itemsArray.length()) {
        val o = itemsArray.getJSONObject(i)
        items.add(StockTransactionItem(
            productId = o.optString("productId", ""),
            productName = o.optString("productName", ""),
            quantityBags = o.optInt("quantityBags", 0),
            metricTons = o.optDouble("metricTons", 0.0),
            ratePerBag = o.optDouble("ratePerBag", 0.0),
            batchNo = o.optString("batchNo", ""),
            bayLocation = o.optString("bayLocation", "")
        ))
    }
    return StockTransaction(
        id = id,
        slipNo = slipNo,
        type = try { TransactionType.valueOf(type) } catch (e: Exception) { TransactionType.OUTWARD },
        timestamp = timestamp,
        partyName = partyName,
        vehicleNo = vehicleNo,
        driverName = driverName,
        driverPhone = driverPhone,
        challanNo = challanNo,
        ewbNo = ewbNo,
        destinationSite = destinationSite,
        totalBags = totalBags,
        totalMetricTons = totalMetricTons,
        totalAmount = totalAmount,
        items = items,
        dispatchedBy = dispatchedBy,
        customerPhone = customerPhone
    )
}

private fun StockTransaction.toEntity(): TransactionEntity {
    val arr = org.json.JSONArray()
    for (item in items) {
        val o = org.json.JSONObject()
        o.put("productId", item.productId)
        o.put("productName", item.productName)
        o.put("quantityBags", item.quantityBags)
        o.put("metricTons", item.metricTons)
        o.put("ratePerBag", item.ratePerBag)
        o.put("batchNo", item.batchNo)
        o.put("bayLocation", item.bayLocation)
        arr.put(o)
    }
    return TransactionEntity(
        id = id,
        slipNo = slipNo,
        type = type.name,
        timestamp = timestamp,
        partyName = partyName,
        customerPhone = customerPhone,
        vehicleNo = vehicleNo,
        driverName = driverName,
        driverPhone = driverPhone,
        challanNo = challanNo,
        ewbNo = ewbNo,
        destinationSite = destinationSite,
        totalBags = totalBags,
        totalMetricTons = totalMetricTons,
        totalAmount = totalAmount,
        dispatchedBy = dispatchedBy,
        itemsJson = arr.toString()
    )
}

private fun StockTransaction.toJson(): String {
    val o = org.json.JSONObject()
    o.put("id", id)
    o.put("slipNo", slipNo)
    o.put("type", type.name)
    o.put("timestamp", timestamp)
    o.put("partyName", partyName)
    o.put("customerPhone", customerPhone)
    o.put("vehicleNo", vehicleNo)
    o.put("driverName", driverName)
    o.put("driverPhone", driverPhone)
    o.put("challanNo", challanNo)
    o.put("ewbNo", ewbNo)
    o.put("destinationSite", destinationSite)
    o.put("totalBags", totalBags)
    o.put("totalMetricTons", totalMetricTons)
    o.put("totalAmount", totalAmount)
    o.put("dispatchedBy", dispatchedBy)
    return o.toString()
}

private fun Product.toJson(): String {
    val o = org.json.JSONObject()
    o.put("id", id)
    o.put("name", name)
    o.put("grade", grade)
    o.put("unit", unit)
    o.put("weightPerBagKg", weightPerBagKg)
    o.put("defaultRatePerBag", defaultRatePerBag)
    o.put("bayLocation", bayLocation)
    o.put("currentStockBags", currentStockBags)
    o.put("batchNo", batchNo)
    o.put("imageUrl", imageUrl ?: "")
    o.put("isActive", isActive)
    return o.toString()
}