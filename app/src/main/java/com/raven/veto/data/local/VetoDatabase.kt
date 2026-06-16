package com.raven.veto.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DailyUsageEntity::class, AppProfileEntity::class], version = 1, exportSchema = false)
abstract class VetoDatabase : RoomDatabase() {
    abstract fun vetoDao(): VetoDao
}
