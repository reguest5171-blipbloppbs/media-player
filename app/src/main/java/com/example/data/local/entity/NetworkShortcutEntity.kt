package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_shortcuts")
data class NetworkShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val serverId: Long? = null,
    val serverName: String = "",
    val serverType: String = "FTP", // "FTP", "SMB", "URL"
    val targetPath: String, // Remote path or URL
    val isDirectory: Boolean = true,
    val addedTimestamp: Long = System.currentTimeMillis()
)
