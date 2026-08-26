package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_servers")
data class NetworkServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: String, // "FTP" or "SMB"
    val host: String,
    val port: Int = 21,
    val username: String = "",
    val password: String = "",
    val initialPath: String = "/",
    val isAnonymous: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stream_bookmarks")
data class StreamBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val category: String = "Live Stream",
    val addedTimestamp: Long = System.currentTimeMillis()
)
