package com.example.bhpos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutboxDao {
    @Insert
    suspend fun enqueue(entity: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY createdAt ASC")
    suspend fun getAll(): List<OutboxEntity>

    @Query("DELETE FROM outbox WHERE rowId = :rowId")
    suspend fun delete(rowId: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE rowId = :rowId")
    suspend fun incrementAttempts(rowId: Long)
}