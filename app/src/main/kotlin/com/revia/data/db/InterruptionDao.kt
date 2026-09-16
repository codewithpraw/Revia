package com.revia.data.db

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

    /**
     * The interruption waiting to be shown for [packageName], if there is one recent
     * enough to still be worth surfacing. Replaces an in-memory map so a pending card
     * survives the detection service being killed.
     */
    @Query(
        "SELECT * FROM interruptions " +
            "WHERE packageName = :packageName AND surfaced = 0 AND timestamp >= :notBefore " +
            "ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun findPendingFor(packageName: String, notBefore: Long): Interruption?

    @Query("UPDATE interruptions SET surfaced = 1 WHERE id = :id")
    suspend fun markSurfaced(id: Int)

    @Query("DELETE FROM interruptions WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM interruptions")
    suspend fun clearAll()
}
