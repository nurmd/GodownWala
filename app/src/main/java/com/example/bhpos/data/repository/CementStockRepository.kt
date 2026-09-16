package com.example.bhpos.data.repository

import com.example.bhpos.domain.model.CementProduct
import com.example.bhpos.domain.model.DashboardTelemetry
import com.example.bhpos.domain.model.Party
import com.example.bhpos.domain.model.StockTransaction
import kotlinx.coroutines.flow.Flow

interface CementStockRepository {
    fun getProductsFlow(): Flow<List<CementProduct>>
    fun getParties(): List<Party>
    fun getDashboardTelemetry(): Flow<DashboardTelemetry>
    fun getTransactionsFlow(): Flow<List<StockTransaction>>
    suspend fun getTransactionBySlipNo(slipNo: String): StockTransaction?
    suspend fun recordDispatch(transaction: StockTransaction): Result<StockTransaction>
    suspend fun recordStockIn(productId: String, bags: Int, batchNo: String, bayLocation: String): Result<Unit>
    suspend fun getLastSlip(): StockTransaction?
}
