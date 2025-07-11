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
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.prashik.firewallapp.model.data.TrafficLogResponse
import com.prashik.firewallapp.util.UidResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

class FirewallVpnService : VpnService() {

    companion object {
        private const val START_VPN_ACTION = "START_VPN_SERVICE"
        private const val STOP_VPN_ACTION = "STOP_VPN_SERVICE"
        private var vpnInterface: ParcelFileDescriptor? = null
        private var notificationManager: NotificationManager? = null
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 1
        private val vpnScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val trafficLogs = mutableStateListOf<TrafficLogResponse>()
        private val udpSocketMap = ConcurrentHashMap<Int, DatagramSocket>()
    }

    private lateinit var udpSocket: DatagramSocket

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
                stopVpn()
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

    private fun startVpn() {
        if (vpnInterface != null) return

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)

        vpnInterface = builder.establish()
        val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
        udpSocket = DatagramSocket()
        udpSocket.broadcast = true

        vpnScope.launch {
            try {
                readPackets(inputStream)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d("VPN", "VPN readPackets cancelled cleanly")
                } else {
                    Log.e("VPN", "Error in readPackets", e)
                }
            }
        }
    }

    private suspend fun readPackets(inputStream: FileInputStream) {
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
                                trafficLogResponse
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


                        val lastLog = trafficLogs.lastOrNull()

                        if (trafficLogs.size > 50) trafficLogs.removeFirstOrNull()

                        if (!trafficLogResponse.appName.contains("Unknown") &&
                            (lastLog?.srcIp != trafficLogResponse.srcIp ||
                                    lastLog.srcPort != trafficLogResponse.srcPort ||
                                    lastLog.dstIp != trafficLogResponse.dstIp ||
                                    lastLog.dstPort != trafficLogResponse.dstPort)
                        ) {
                            trafficLogs.add(trafficLogResponse)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VPN", "Error reading packets", e)
                break
            }
        }
    }

    private fun stopVpn() {
        stopSelf()
        vpnScope.cancel()
        vpnScope.coroutineContext[Job]?.invokeOnCompletion {
            try {
                vpnInterface?.close()
                vpnInterface = null
                udpSocketMap.values.forEach { it.close() }
                udpSocketMap.clear()
                udpSocket.close()
            } catch (e: Exception) {
                Log.e("VPN", "Error during VPN stop cleanup", e)
            }
        }
    }

    private fun forwardUdpPacket(
        packetData: ByteArray,
        length: Int,
        trafficLogResponse: TrafficLogResponse
    ) {
        try {
            val dstIp = trafficLogResponse.dstIp
            val dstPort = trafficLogResponse.dstPort
            val srcPort = trafficLogResponse.srcPort

            if (dstIp == "Unknown" || dstPort == -1 || srcPort == -1) {
                return
            }

            val udpPayload = NativeBridge.extractUdpPayload(packetData, length)
            if (udpPayload == null) {
                Log.e(
                    "UDP_FORWARD",
                    "Failed to extract UDP payload, length=$length, first bytes=${
                        packetData.take(10).joinToString(",")
                    }"
                )
                return
            }
        } catch (e: Exception) {
            Log.e("UDP_FORWARD", "Error forwarding UDP packet", e)
        }
    }

    private fun launchUdpResponseListener(socket: DatagramSocket, srcPort: Int) {
        vpnScope.launch {
            val buffer = ByteArray(4096)
            while (isActive) {
                try {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(responsePacket)

                    val responseData = responsePacket.data.copyOf(responsePacket.length)

                    val ipv4Packet = NativeBridge.buildIpv4UdpPacket(
                        srcIp = responsePacket.address.hostAddress,
                        dstIp = "10.0.0.2", // VPN TUN address
                        srcPort = responsePacket.port,
                        dstPort = srcPort,
                        udpPayload = responseData
                    )

                    val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
                    outputStream.write(ipv4Packet)
                } catch (e: Exception) {
                    Log.e("UDP_RESPONSE", "Error receiving UDP response", e)
                    break
                }
            }
        }
    }
}