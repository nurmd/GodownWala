package com.example.bhpos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val businessId: String = "",
    val slipNo: String,
    val type: String = "OUTWARD",
    val timestamp: Long = 0L,
    val dateStr: String = "",
    val timeStr: String = "",
    val partyName: String = "",
    val customerPhone: String = "",
    val vehicleNo: String = "",
    val driverName: String = "",
    val driverPhone: String = "",
    val challanNo: String = "",
    val ewbNo: String = "",
    val destinationSite: String = "",
    val totalBags: Int = 0,
    val totalMetricTons: Double = 0.0,
    val totalAmount: Double = 0.0,
    val dispatchedBy: String = "Admin",
    val itemsJson: String = "[]"
)