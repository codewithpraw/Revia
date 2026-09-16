package com.revia.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Interruption::class], version = 3, exportSchema = false)
abstract class ReviaDatabase : RoomDatabase() {

    abstract fun interruptionDao(): InterruptionDao

    companion object {
        @Volatile
        private var INSTANCE: ReviaDatabase? = null

        /** v2 adds packageName so history rows can show the app's own icon. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE interruptions ADD COLUMN packageName TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v3 adds surfaced, plus an index on packageName: pending cards moved out of
         * memory and into the database, and looking one up runs on every app switch.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE interruptions ADD COLUMN surfaced INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_interruptions_packageName " +
                        "ON interruptions (packageName)"
                )
            }
        }

        fun getInstance(context: Context): ReviaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReviaDatabase::class.java,
                    "revia.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
        }
    }
}
