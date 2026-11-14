package com.example.bhpos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProductEntity::class, PartyEntity::class, TransactionEntity::class, OutboxEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GodownDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun partyDao(): PartyDao
    abstract fun transactionDao(): TransactionDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile private var instance: GodownDatabase? = null

        fun get(context: Context): GodownDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GodownDatabase::class.java,
                    "godownwala.db"
                ).fallbackToDestructiveMigration(true).build().also { instance = it }
            }
    }
}