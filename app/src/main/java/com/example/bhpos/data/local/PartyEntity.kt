package com.example.bhpos.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey val id: String,
    val businessId: String = "",
    val name: String,
    val type: String = "customer",
    val phone: String = "",
    val gstin: String = "UNREGISTERED",
    val defaultDestination: String = "Site Delivery",
    val accountNo: String = "ACC-000",
    val isActive: Boolean = true
)