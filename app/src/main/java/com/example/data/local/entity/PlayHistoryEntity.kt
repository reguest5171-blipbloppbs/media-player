package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val mediaUri: String,
    val mediaPath: String,
    val title: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val decoderMode: String = "HW",
    val isCompleted: Boolean = false
)
