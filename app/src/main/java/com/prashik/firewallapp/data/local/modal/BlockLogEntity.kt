package com.prashik.firewallapp.data.local.modal

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "block_log", primaryKeys = ["appName", "protocol", "dstIp", "dstPort"])
data class BlockLogEntity(
//    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val appName: String,
    val protocol: String,
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
    val timestamp: String
)
