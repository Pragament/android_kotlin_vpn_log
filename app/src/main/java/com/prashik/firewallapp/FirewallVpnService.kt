@file:OptIn(ExperimentalAtomicApi::class)

package com.prashik.firewallapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.prashik.firewallapp.data.local.dao.BlockLogDao
import com.prashik.firewallapp.data.local.dao.AppAddressDao
import com.prashik.firewallapp.data.local.modal.BlockLogEntity
import com.prashik.firewallapp.data.local.modal.AppAddressEntity
import com.prashik.firewallapp.data.local.modal.TrafficLogResponse
import com.prashik.firewallapp.data.repository.PreferenceKeys
import com.prashik.firewallapp.data.repository.dataStore
import com.prashik.firewallapp.util.UidResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.coroutineContext

class FirewallVpnService : VpnService() {

    companion object {
        var isRunning = AtomicBoolean(false)
        private const val START_VPN_ACTION = "START_VPN_SERVICE"
        private const val STOP_VPN_ACTION = "STOP_VPN_SERVICE"
        private var vpnInterface: ParcelFileDescriptor? = null
        private var notificationManager: NotificationManager? = null
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 1
        private val vpnScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private var blockedAppCache = MutableStateFlow<Set<String>>(emptySet())
    private val logDao by lazy { get<BlockLogDao>() }
    private val appAddressDao by lazy { get<AppAddressDao>() }
    private lateinit var packetForwarder: PacketForwarder

    override fun onCreate() {
        super.onCreate()
        packetForwarder = PacketForwarder(this)
        observeBlockedApps()
    }

    private fun observeBlockedApps() {
        vpnScope.launch {
            var lastBlockedApps: Set<String> = emptySet()
            dataStore.data
                .map { it[PreferenceKeys.BLOCKED_SET] ?: emptySet() }
                .distinctUntilChanged()
                .collect { newSet ->
                    blockedAppCache.value = newSet
                    if (isRunning.load() && newSet != lastBlockedApps) {
                        lastBlockedApps = newSet
                        Log.d("VPN", "Blocked apps changed: restarting VPN")
                        restartVpn()
                    }
                }
        }
    }

    private fun restartVpn() {
        vpnScope.launch {
            onDestroy()
            delay(300)
            startVpn()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            START_VPN_ACTION -> {
                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)
                startVpn()
            }

            STOP_VPN_ACTION -> {
                onDestroy()
            }

            else -> {}
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW)
            notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotification(): Notification {
        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Firewall VPN Running")
            .setContentTitle("Monitoring traffic")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
        return notification.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.store(false)
        stopSelf()
        packetForwarder.cleanup()
        vpnScope.cancel()
        vpnScope.coroutineContext[Job]?.invokeOnCompletion {
            try {
                vpnInterface?.close()
                vpnInterface = null
            } catch (e: Exception) {
                Log.e("VPN", "Error during VPN stop cleanup", e)
            }
        }
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")

        // Exclude VPN app itself from using the VPN
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        vpnInterface = builder.establish()
        isRunning.store(true)
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)

        vpnScope.launch {
            try {
                readPackets(inputStream, outputStream)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d("VPN", "VPN readPackets cancelled cleanly")
                } else {
                    Log.e("VPN", "Error in readPackets", e)
                }
            }
        }
    }

    private suspend fun readPackets(inputStream: FileInputStream, outputStream: FileOutputStream) {
        val byteArray = ByteArray(32767)

        while (coroutineContext.isActive) {
            try {
                val length = inputStream.read(byteArray)

                if (length > 0) {
                    val jsonString = NativeBridge.parseRealPacket(byteArray, length)
                    val trafficLogResponse =
                        Gson().fromJson(jsonString, TrafficLogResponse::class.java)
                    if (trafficLogResponse?.srcIp != "Unknown" ||
                        trafficLogResponse.srcPort != -1 ||
                        trafficLogResponse.dstIp != "Unknown" ||
                        trafficLogResponse.dstPort != -1
                    ) {
                        val protocol = when (trafficLogResponse.protocolByte) {
                            "TCP" -> 6
                            "UDP" -> 17
                            else -> -1
                        }
                        
                        val connectivityManager =
                            getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                        val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            UidResolver.getUidFromIpPort(
                                trafficLogResponse.srcIp, trafficLogResponse.srcPort,
                                trafficLogResponse.dstIp, trafficLogResponse.dstPort,
                                protocol, connectivityManager
                            )
                        } else {
                            val localHex = UidResolver.ipPortToLocalHex(
                                trafficLogResponse.srcIp,
                                trafficLogResponse.srcPort
                            )
                            UidResolver.findUidForConnection(
                                localHex,
                                trafficLogResponse.protocol
                            )
                        }
                        val appName = when (uid) {
                            0 -> "Root / Kernel"
                            -1 -> "Unknown UID"
                            else -> {
                                uid?.let { UidResolver.getAppNameFromUid(this, it) }
                                    ?: "Unknown App (UID: $uid)"
                            }
                        }

                        trafficLogResponse.appName = appName

                        // Get the actual app's package name
                        val sourcePackageName = uid?.let { UidResolver.getPackageNameFromUid(this, it) }

                        // Track unique addresses for this app
                        sourcePackageName?.let { pkg ->
                            trackAppAddress(pkg, appName, trafficLogResponse)
                        }

                        // Check if specific address is blocked
                        val isAddressBlocked = sourcePackageName?.let { pkg ->
                            isAddressBlockedForApp(pkg, trafficLogResponse.dstIp, trafficLogResponse.dstPort, trafficLogResponse.protocolByte)
                        } ?: false

                        // Check if app is blocked
                        val isAppBlocked = sourcePackageName?.let { blockedAppCache.value.contains(it) } ?: false

                        if (isAppBlocked || isAddressBlocked) {
                            // Log blocked packet but don't forward
                            Log.d("VPN", "Blocking packet from $sourcePackageName - App blocked: $isAppBlocked, Address blocked: $isAddressBlocked")
                            if (!trafficLogResponse.appName.contains("Unknown")) {
                                logBlockedPacket(trafficLogResponse)
                                logDao.deleteExtraLogs()
                            }
                            continue // Skip this packet
                        }

                        // Forward allowed packets
                        Log.d("VPN", "Forwarding packet: ${trafficLogResponse.protocolByte} ${trafficLogResponse.srcIp}:${trafficLogResponse.srcPort} -> ${trafficLogResponse.dstIp}:${trafficLogResponse.dstPort}")
                        
                        packetForwarder.forwardPacket(
                            byteArray,
                            length,
                            trafficLogResponse,
                            outputStream
                        )

                        // Log traffic for monitoring (not blocking)
                        if (!trafficLogResponse.appName.contains("Unknown")) {
                            logBlockedPacket(trafficLogResponse)
                            logDao.deleteExtraLogs()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VPN", "Error reading packets", e)
                break
            }
        }
    }

    private fun logBlockedPacket(
        trafficLogResponse: TrafficLogResponse
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            logDao.insert(
                blockLog = BlockLogEntity(
                    appName = trafficLogResponse.appName,
                    protocol = trafficLogResponse.protocolByte,
                    srcIp = trafficLogResponse.srcIp,
                    srcPort = trafficLogResponse.srcPort,
                    dstIp = trafficLogResponse.dstIp,
                    dstPort = trafficLogResponse.dstPort,
                    timestamp = trafficLogResponse.timeStamp,
                )
            )
        }
    }

    private fun trackAppAddress(packageName: String, appName: String, traffic: TrafficLogResponse) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existing = appAddressDao.getAddress(
                    packageName, traffic.dstIp, traffic.dstPort, traffic.protocolByte
                )
                
                if (existing != null) {
                    // Update last seen timestamp
                    appAddressDao.updateLastSeen(
                        packageName, traffic.dstIp, traffic.dstPort, traffic.protocolByte, traffic.timeStamp
                    )
                } else {
                    // Insert new address
                    appAddressDao.insertAddress(
                        AppAddressEntity(
                            packageName = packageName,
                            appName = appName,
                            ipAddress = traffic.dstIp,
                            port = traffic.dstPort,
                            protocol = traffic.protocolByte,
                            isBlocked = false,
                            firstSeen = traffic.timeStamp,
                            lastSeen = traffic.timeStamp
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("VPN", "Error tracking address", e)
            }
        }
    }

    private suspend fun isAddressBlockedForApp(packageName: String, ip: String, port: Int, protocol: String): Boolean {
        return try {
            val address = appAddressDao.getAddress(packageName, ip, port, protocol)
            address?.isBlocked ?: false
        } catch (e: Exception) {
            Log.e("VPN", "Error checking address block status", e)
            false
        }
    }
}