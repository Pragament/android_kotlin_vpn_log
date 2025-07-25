package com.prashik.firewallapp

object NativeBridge {
    external fun parseRealPacket(packet: ByteArray, length: Int): String

    external fun extractUdpPayload(packetData: ByteArray, length: Int): ByteArray?

    external fun buildIpv4UdpPacketNative(
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int,
        udpPayload: ByteArray
    ): ByteArray

    fun buildIpv4UdpPacket(
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int,
        udpPayload: ByteArray
    ): ByteArray {
        return buildIpv4UdpPacketNative(srcIp, dstIp, srcPort, dstPort, udpPayload)
    }

    external fun buildUdpResponsePacket(
        payload: ByteArray,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int
    ): ByteArray

    external fun extractTcpPayload(packetData: ByteArray, length: Int): ByteArray?
    external fun buildTcpResponsePacket(
        payload: ByteArray,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int
    ): ByteArray
}