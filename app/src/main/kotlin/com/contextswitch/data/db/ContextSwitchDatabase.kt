package com.contextswitch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Interruption::class], version = 1, exportSchema = false)
abstract class ContextSwitchDatabase : RoomDatabase() {

    abstract fun interruptionDao(): InterruptionDao

    companion object {
        @Volatile
        private var INSTANCE: ContextSwitchDatabase? = null

        fun getInstance(context: Context): ContextSwitchDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ContextSwitchDatabase::class.java,
                    "context_switch.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
