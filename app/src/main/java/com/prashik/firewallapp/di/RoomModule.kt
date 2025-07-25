package com.prashik.firewallapp.di

import android.app.Application
import androidx.room.Room
import com.prashik.firewallapp.data.local.dao.BlockLogDao
import com.prashik.firewallapp.data.local.database.BlockLogDatabase
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class RoomModule {

    @Single
    fun provideDatabase(application: Application): BlockLogDatabase {
        return Room.databaseBuilder(
            application,
            BlockLogDatabase::class.java,
            "block_log"
        ).fallbackToDestructiveMigration(false).build()
    }

    @Single
    fun provideDao(blockLogDatabase: BlockLogDatabase): BlockLogDao {
        return blockLogDatabase.getBlockLogDao()
    }
}