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
import com.prashik.firewallapp.data.local.modal.BlockLogEntity
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
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
        private val tcpSocketMap = ConcurrentHashMap<String, Socket>()
    }

    private var blockedAppCache = MutableStateFlow<Set<String>>(emptySet())

    private var udpSocket: DatagramSocket? = null
    private val logDao by lazy { get<BlockLogDao>() }

    override fun onCreate() {
        super.onCreate()

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
        vpnScope.cancel()
        vpnScope.coroutineContext[Job]?.invokeOnCompletion {
            try {
                vpnInterface?.close()
                vpnInterface = null
                tcpSocketMap.values.forEach { it.close() }
                tcpSocketMap.clear()
                udpSocket?.close()
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

        packageManager.getInstalledApplications(0).forEach { appInfo ->
            if (!blockedAppCache.value.contains(appInfo.packageName)) {
                try {
                    builder.addDisallowedApplication(appInfo.packageName)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        vpnInterface = builder.establish()
        isRunning.store(true)
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
        udpSocket = DatagramSocket()
        udpSocket?.broadcast = true

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
                        if (trafficLogResponse.protocolByte == "UDP") {
                            forwardUdpPacket(
                                byteArray,
                                length,
                                trafficLogResponse,
                                outputStream
                            )
                        } else if (trafficLogResponse.protocolByte == "TCP") {
                            forwardTcpPacket(
                                packetData = byteArray,
                                length = length,
                                trafficLogResponse,
                                outputStream
                            )
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

    private fun forwardUdpPacket(
        packetData: ByteArray,
        length: Int,
        trafficLogResponse: TrafficLogResponse,
        vpnOutputStream: FileOutputStream
    ) {
        try {
            val dstIp = trafficLogResponse.dstIp
            val dstPort = trafficLogResponse.dstPort
            val srcPort = trafficLogResponse.srcPort

            if (dstIp == "Unknown" || dstPort == -1 || srcPort == -1) {
                return
            }

            val udpPayload = NativeBridge.extractUdpPayload(packetData, length) ?: return

            if (vpnInterface?.fileDescriptor?.valid() == true) {
                protect(udpSocket)
            } else {
                Log.w("UDP_FORWARD", "VPN interface is null/closed — skipping protect()")
            }

            val address = InetAddress.getByName(dstIp)
            val requestPacket = DatagramPacket(udpPayload, udpPayload.size, address, dstPort)
            udpSocket?.send(requestPacket)

            //receiving the response
            val buffer = ByteArray(2048)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            udpSocket?.soTimeout = 300

            try {
                udpSocket?.receive(responsePacket)

                val responseData = buffer.copyOf(responsePacket.length)

                val builtPacket = NativeBridge.buildUdpResponsePacket(
                    responseData,
                    responsePacket.address.hostAddress, // real server IP
                    responsePacket.port,
                    "10.0.0.2", // our VPN IP
                    srcPort
                )
                vpnOutputStream.write(builtPacket)

            } catch (_: SocketTimeoutException) {
                Log.w("UDP_FORWARD", "No response received from $dstIp:$dstPort")
            }
        } catch (e: Exception) {
            Log.e("UDP_FORWARD", "Error forwarding UDP packet", e)
        }
    }

    private fun forwardTcpPacket(
        packetData: ByteArray,
        length: Int,
        traffic: TrafficLogResponse,
        vpnOutputStream: FileOutputStream
    ) {
        try {
            val key = "${traffic.srcIp}:${traffic.srcPort}->${traffic.dstIp}:${traffic.dstPort}"

            val tcpPayload = NativeBridge.extractTcpPayload(packetData, length) ?: return

            val socket = tcpSocketMap.getOrPut(key) {
                val s = Socket()
                protect(s)
                s.connect(InetSocketAddress(traffic.dstIp, traffic.dstPort), 1500)
                startReadingFromSocketToVpn(s, traffic, vpnOutputStream)
                s
            }

            socket.getOutputStream().write(tcpPayload)
            socket.getOutputStream().flush()

        } catch (e: Exception) {
            Log.e("TCP_FORWARD", "Error forwarding TCP packet", e)
        }
    }

    private fun startReadingFromSocketToVpn(
        socket: Socket,
        traffic: TrafficLogResponse,
        vpnOutputStream: FileOutputStream
    ) {
        vpnScope.launch {
            try {
                val buffer = ByteArray(4096)
                val input = socket.getInputStream()

                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break

                    val responseData = buffer.copyOf(bytesRead)

                    val builtPacket = NativeBridge.buildTcpResponsePacket(
                        responseData,
                        socket.inetAddress.hostAddress,
                        socket.port,
                        "10.0.0.2", // your VPN IP
                        traffic.srcPort
                    )

                    vpnOutputStream.write(builtPacket)
                }
            } catch (e: Exception) {
                Log.e("TCP_STREAM", "Socket read error", e)
            } finally {
                socket.close()
                val key = "${traffic.srcIp}:${traffic.srcPort}->${traffic.dstIp}:${traffic.dstPort}"
                tcpSocketMap.remove(key)
            }
        }
    }
}