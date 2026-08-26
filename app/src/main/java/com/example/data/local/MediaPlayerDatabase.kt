package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.MediaPlayerDao
import com.example.data.local.entity.NetworkServerEntity
import com.example.data.local.entity.PlayHistoryEntity
import com.example.data.local.entity.StreamBookmarkEntity

@Database(
    entities = [
        PlayHistoryEntity::class,
        NetworkServerEntity::class,
        StreamBookmarkEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MediaPlayerDatabase : RoomDatabase() {
    abstract fun mediaPlayerDao(): MediaPlayerDao

    companion object {
        @Volatile
        private var INSTANCE: MediaPlayerDatabase? = null

        fun getDatabase(context: Context): MediaPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MediaPlayerDatabase::class.java,
                    "media_player_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
