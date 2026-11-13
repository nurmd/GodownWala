package com.example.bhpos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val businessId: String = "",
    val name: String,
    val grade: String = "Standard",
    val unit: String = "Units",
    val weightPerBagKg: Double = 50.0,
    val defaultRatePerBag: Double = 0.0,
    val bayLocation: String = "Unassigned",
    val currentStockBags: Int = 0,
    val batchNo: String = "NEW",
    val imageUrl: String? = null,
    val isActive: Boolean = true
)