package com.example.bhpos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties")
    suspend fun getAll(): List<PartyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PartyEntity)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun delete(id: String)
}