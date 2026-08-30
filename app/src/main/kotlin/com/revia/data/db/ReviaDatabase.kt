package com.revia.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Interruption::class], version = 1, exportSchema = false)
abstract class ReviaDatabase : RoomDatabase() {

    abstract fun interruptionDao(): InterruptionDao

    companion object {
        @Volatile
        private var INSTANCE: ReviaDatabase? = null

        fun getInstance(context: Context): ReviaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReviaDatabase::class.java,
                    "revia.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
