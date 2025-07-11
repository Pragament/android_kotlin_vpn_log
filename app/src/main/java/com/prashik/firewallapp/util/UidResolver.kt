package com.prashik.firewallapp.util

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import java.io.File
import java.net.InetSocketAddress

object UidResolver {

    @RequiresApi(Build.VERSION_CODES.Q)
    fun getUidFromIpPort(
        srcIp: String,
        srcPort: Int,
        destIp: String,
        destPort: Int,
        protocol: Int,
        connectivityManager: ConnectivityManager
    ): Int? {

        if (protocol != 6 && protocol != 17) return null
        val uid = connectivityManager.getConnectionOwnerUid(
            protocol,
            InetSocketAddress(srcIp, srcPort),
            InetSocketAddress(destIp, destPort)
        )

        return if (uid != Process.INVALID_UID) uid else -1
    }

    fun ipPortToLocalHex(
        sourceIp: String,
        sourcePort: Int
    ): String {
        val ipHex = sourceIp.split(".")
            .map { it.toInt().toString(16).padStart(2, '0') }
            .reversed()
            .joinToString("")

        val portHex = Integer.toHexString(sourcePort).padStart(4, '0').uppercase()
        return "$ipHex:$portHex"
    }

    fun findUidForConnection(localHex: String, protocol: String): Int? {

        val filePath = when {
            protocol.equals("TCP", ignoreCase = true)  -> "/proc/net/tcp"
            protocol.equals("UDP", ignoreCase = true)  -> "/proc/net/udp"
            else -> return null // Unsupported protocol
        }
        File(filePath).useLines { lines ->
            lines.forEach { line ->
                if (line.contains(localHex)) {
                    val columns = line.trim().split(Regex("\\s+"))
                    return columns.getOrNull(7)?.toIntOrNull()
                }
            }
        }
        return null
    }

    fun getAppNameFromUid(context: Context, uid: Int): String? {
        return try {
            val pm = context.packageManager
            val packageNames = pm.getPackagesForUid(uid)

            val appNames = packageNames?.mapNotNull { pkg ->
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    null
                }
            }
            return appNames?.joinToString(", ")?.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }
}