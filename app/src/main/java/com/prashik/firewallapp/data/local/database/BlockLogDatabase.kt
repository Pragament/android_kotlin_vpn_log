package com.prashik.firewallapp.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.prashik.firewallapp.data.local.dao.BlockLogDao
import com.prashik.firewallapp.data.local.modal.BlockLogEntity


@Database(entities = [BlockLogEntity::class], version = 2, exportSchema = false)
abstract class BlockLogDatabase: RoomDatabase() {
    abstract fun getBlockLogDao(): BlockLogDao
}