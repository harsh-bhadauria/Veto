package com.raven.veto.di

import android.content.Context
import androidx.room.Room
import com.raven.veto.data.local.PreferencesManager
import com.raven.veto.data.local.VetoDao
import com.raven.veto.data.local.VetoDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVetoDatabase(@ApplicationContext context: Context): VetoDatabase {
        return Room.databaseBuilder(
            context,
            VetoDatabase::class.java,
            "veto_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideVetoDao(database: VetoDatabase): VetoDao {
        return database.vetoDao()
    }

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }
}
