package com.contextswitch.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InterruptionDao {

    @Insert
    suspend fun insert(interruption: Interruption): Long

    @Update
    suspend fun update(interruption: Interruption)

    @Query("SELECT * FROM interruptions ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<Interruption>>

    @Query("SELECT * FROM interruptions WHERE appName = :appName ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestForApp(appName: String): Interruption?

    @Query("DELETE FROM interruptions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM interruptions")
    suspend fun clearAll()
}
