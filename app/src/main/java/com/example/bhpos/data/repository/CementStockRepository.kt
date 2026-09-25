package com.example.bhpos.data.repository

import com.example.bhpos.domain.model.Product
import com.example.bhpos.domain.model.DashboardTelemetry
import com.example.bhpos.domain.model.Party
import com.example.bhpos.domain.model.StockTransaction
import kotlinx.coroutines.flow.Flow

interface CementStockRepository {
    fun setProducts(products: List<Product>)
    fun getProductsFlow(): Flow<List<Product>>
    fun getCurrentProducts(): List<Product>
    fun setTransactions(txs: List<StockTransaction>)
    fun setParties(parties: List<Party>)
    fun getParties(): List<Party>
    fun getDashboardTelemetry(): Flow<DashboardTelemetry>
    fun getTransactionsFlow(): Flow<List<StockTransaction>>
    fun getCurrentTransactions(): List<StockTransaction>
    suspend fun getTransactionBySlipNo(slipNo: String): StockTransaction?
    suspend fun recordDispatch(transaction: StockTransaction): Result<StockTransaction>
    suspend fun updateDispatch(transaction: StockTransaction): Result<StockTransaction>
    suspend fun recordStockIn(productId: String, bags: Int, batchNo: String, bayLocation: String): Result<String>
    suspend fun getLastSlip(): StockTransaction?
    suspend fun updateProduct(product: Product): Result<Unit>
    suspend fun addProduct(product: Product): Result<Unit>
}
