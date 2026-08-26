package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.NetworkServerEntity
import com.example.data.local.entity.PlayHistoryEntity
import com.example.data.local.entity.StreamBookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaPlayerDao {
    // Play History
    @Query("SELECT * FROM play_history ORDER BY lastPlayedTimestamp DESC")
    fun getAllPlayHistory(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE mediaUri = :uri LIMIT 1")
    suspend fun getPlayHistoryForUri(uri: String): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlayHistory(history: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE mediaUri = :uri")
    suspend fun deletePlayHistory(uri: String)

    @Query("DELETE FROM play_history")
    suspend fun clearAllHistory()

    // Network Servers
    @Query("SELECT * FROM network_servers ORDER BY createdAt DESC")
    fun getAllServers(): Flow<List<NetworkServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: NetworkServerEntity): Long

    @Query("DELETE FROM network_servers WHERE id = :id")
    suspend fun deleteServer(id: Long)

    // Stream Bookmarks
    @Query("SELECT * FROM stream_bookmarks ORDER BY addedTimestamp DESC")
    fun getAllBookmarks(): Flow<List<StreamBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: StreamBookmarkEntity): Long

    @Query("DELETE FROM stream_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)
}
