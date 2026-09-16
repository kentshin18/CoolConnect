package com.example.coolconnect

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TechnicianEntity::class, ProductEntity::class, CompletedJobEntity::class], version = 1)
abstract class CoolConnectDatabase : RoomDatabase() {
    abstract fun dao(): CoolConnectDao

    companion object {
        @Volatile
        private var INSTANCE: CoolConnectDatabase? = null

        fun getDatabase(context: Context): CoolConnectDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CoolConnectDatabase::class.java,
                    "coolconnect_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
