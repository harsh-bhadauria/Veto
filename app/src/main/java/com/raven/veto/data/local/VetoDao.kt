package com.raven.veto.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VetoDao {
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    suspend fun getDailyUsage(date: String): DailyUsageEntity?

    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT :days")
    fun getRecentUsageFlow(days: Int): Flow<List<DailyUsageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyUsage(usage: DailyUsageEntity)

    @Query("SELECT * FROM app_profiles")
    fun getAllAppProfilesFlow(): Flow<List<AppProfileEntity>>

    @Query("SELECT * FROM app_profiles WHERE packageName = :packageName")
    suspend fun getAppProfile(packageName: String): AppProfileEntity?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppProfile(profile: AppProfileEntity)

    @Query("DELETE FROM app_profiles WHERE packageName = :packageName")
    suspend fun deleteAppProfile(packageName: String)
}
