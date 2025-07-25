#include <jni.h>
#include <string>
#include <sys/endian.h>
#include "include/pcapplusplus/RawPacket.h"
#include "include/pcapplusplus/Packet.h"
#include "include/pcapplusplus/IPv4Layer.h"
#include "include/pcapplusplus/IPv6Layer.h"
#include "include/pcapplusplus/TcpLayer.h"
#include "include/pcapplusplus/UdpLayer.h"
#include "include/pcapplusplus/PcapLiveDeviceList.h"
#include "include/pcapplusplus/IcmpV6Layer.h"
#include "include/pcapplusplus/PayloadLayer.h"
#include "include/pcapplusplus/EthLayer.h"

extern "C"
JNIEXPORT jstring JNICALL
Java_com_prashik_firewallapp_NativeBridge_parseRealPacket(
        JNIEnv *env,
        jobject ,
        jbyteArray packet,
        jint length
) {
    jbyte *packetBytes = env->GetByteArrayElements(packet, nullptr);

    timeval tv{};
    gettimeofday(&tv, nullptr);

    pcpp::RawPacket rawPacket(
            reinterpret_cast<const uint8_t *>(packetBytes),
            length,
            tv,
            false,
            pcpp::LINKTYPE_RAW
    );

    pcpp::Packet parsedPacket(&rawPacket);

    std::string appName = "Unknown";
    std::string srcIp = "Unknown";
    std::string dstIp = "Unknown";
    int srcPort = -1;
    int dstPort = -1;
    std::string protocol = "Unknown";
    std::string protocolByte = "Unknown";

    time_t now = time(nullptr);
    char buf[32];
    strftime(buf, sizeof(buf), "%d/%m/%Y %H:%M:%S", localtime(&now));
    std::string timeStamp(buf);

    auto ipv4Layer = parsedPacket.getLayerOfType<pcpp::IPv4Layer>();

    if (ipv4Layer != nullptr) {
        srcIp = ipv4Layer->getSrcIPAddress().toString();
        dstIp = ipv4Layer->getDstIPAddress().toString();
        protocol = "IPv4";
    }

    auto tcpLayer = parsedPacket.getLayerOfType<pcpp::TcpLayer>();
    auto udpLayer = parsedPacket.getLayerOfType<pcpp::UdpLayer>();

    if (tcpLayer != nullptr) {
        srcPort = ntohs(tcpLayer->getTcpHeader()->portSrc);
        dstPort = ntohs(tcpLayer->getTcpHeader()->portDst);
        protocolByte = "TCP";
    } else if (udpLayer != nullptr) {
        srcPort = ntohs(udpLayer->getUdpHeader()->portSrc);
        dstPort = ntohs(udpLayer->getUdpHeader()->portDst);
        protocolByte = "UDP";
    }

    env->ReleaseByteArrayElements(packet, packetBytes, JNI_ABORT);

    std::string jsonResult = "{"
                                "\"appName\":\"" + appName + "\","
                                "\"protocol\":\"" + protocol + "\","
                                "\"protocolByte\":\"" + protocolByte + "\","
                                "\"srcIp\":\"" + srcIp + "\","
                                "\"srcPort\":" + std::to_string(srcPort) + ","
                                "\"dstIp\":\"" + dstIp + "\","
                                "\"dstPort\":" + std::to_string(dstPort) + ","
                                "\"timeStamp\":\"" + timeStamp + "\""
                             "}";

    return env->NewStringUTF(jsonResult.c_str());
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_prashik_firewallapp_NativeBridge_extractUdpPayload(
        JNIEnv *env,
        jobject,
        jbyteArray packetData,
        jint length
        ) {
    jbyte* rawBytes = env->GetByteArrayElements(packetData, nullptr);
    if (rawBytes == nullptr) {
        return nullptr;
    }

    timeval time{};
    gettimeofday(&time, nullptr);

    pcpp::RawPacket rawPacket(
            reinterpret_cast<const uint8_t*>(rawBytes),
            length,
            time,
            false,
            pcpp::LINKTYPE_RAW
    );
    pcpp::Packet parsedPacket(&rawPacket);

    env->ReleaseByteArrayElements(packetData, rawBytes, JNI_ABORT);

    auto udpLayer = parsedPacket.getLayerOfType<pcpp::UdpLayer>();
    if (udpLayer == nullptr) {
        return nullptr;
    }

    pcpp::Layer* nextLayer = udpLayer->getNextLayer();
    auto payloadLayer = dynamic_cast<pcpp::PayloadLayer*>(nextLayer);

    if (payloadLayer == nullptr) {
        return nullptr;
    }

    const uint8_t* payloadData = payloadLayer->getPayload();
    size_t payloadLen = payloadLayer->getPayloadLen();

    if (payloadData == nullptr || payloadLen == 0) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(payloadLen);
    env->SetByteArrayRegion(result, 0, payloadLen, reinterpret_cast<const jbyte*>(payloadData));

    return result;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_prashik_firewallapp_NativeBridge_buildIpv4UdpPacketNative(
        JNIEnv *env,
        jobject ,
        jstring jSrcIp, jstring jDstIp,
        jint jSrcPort, jint jDstPort,
        jbyteArray jPayload
) {

    const char *srcIp = env->GetStringUTFChars(jSrcIp, nullptr);
    const char *dstIp = env->GetStringUTFChars(jDstIp, nullptr);
    int srcPort = jSrcPort;
    int dstPort = jDstPort;

    int payloadLen = env->GetArrayLength(jPayload);
    jbyte *payloadBytes = env->GetByteArrayElements(jPayload, nullptr);

    auto *payloadCopy = new uint8_t[payloadLen];
    memcpy(payloadCopy, payloadBytes, payloadLen);

    pcpp::Packet packet(100);

    pcpp::IPv4Address srcAdd(srcIp);
    pcpp::IPv4Address dstAdd(dstIp);

    pcpp::IPv4Layer ipv4Layer(srcAdd, dstAdd);
    ipv4Layer.getIPv4Header()->timeToLive = 64;
    ipv4Layer.getIPv4Header()->protocol = pcpp::PACKETPP_IPPROTO_UDP;

    pcpp::UdpLayer udpLayer(srcPort, dstPort);

    pcpp::PayloadLayer payloadLayer(payloadCopy, payloadLen);

    packet.addLayer(&ipv4Layer);
    packet.addLayer(&udpLayer);
    packet.addLayer(&payloadLayer);

    packet.computeCalculateFields();

    const uint8_t *rawData = packet.getRawPacket()->getRawData();
    int rawDataLen = packet.getRawPacket()->getRawDataLen();

    jbyteArray result = env->NewByteArray(rawDataLen);
    env->SetByteArrayRegion(result, 0, rawDataLen, reinterpret_cast<const jbyte *>(rawData));

    delete[] payloadCopy;
    env->ReleaseStringUTFChars(jSrcIp, srcIp);
    env->ReleaseStringUTFChars(jDstIp, dstIp);
    env->ReleaseByteArrayElements(jPayload, payloadBytes, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_prashik_firewallapp_NativeBridge_buildUdpResponsePacket(
        JNIEnv *env,
        jobject thiz,
        jbyteArray payload,
        jstring src_ip,
        jint src_port,
        jstring dst_ip,
        jint dst_port
) {
    const char* srcIp = env->GetStringUTFChars(src_ip, nullptr);
    const char* dstIp = env->GetStringUTFChars(src_ip, nullptr);

    jbyte* payloadBytes = env->GetByteArrayElements(payload, nullptr);
    jsize payloadLen = env->GetArrayLength(payload);

    // Build IP + UDP + Payload
    pcpp::IPv4Address ipSrc(srcIp);
    pcpp::IPv4Address ipDst(dstIp);

    pcpp::IPv4Layer ipLayer(ipSrc, ipDst);
    ipLayer.getIPv4Header()->timeToLive = 64;

    pcpp::UdpLayer udpLayer(src_port, dst_port);
    pcpp::PayloadLayer payloadLayer(reinterpret_cast<const uint8_t*>(payloadBytes), static_cast<size_t>(payloadLen));


    pcpp::Packet packet(100);
    packet.addLayer(&ipLayer);
    packet.addLayer(&udpLayer);
    packet.addLayer(&payloadLayer);
    packet.computeCalculateFields();

    // Final packet as byte array
    int totalLen = packet.getRawPacket()->getRawDataLen();
    const uint8_t* rawData = packet.getRawPacket()->getRawData();

    jbyteArray result = env->NewByteArray(totalLen);
    env->SetByteArrayRegion(result, 0, totalLen, (const jbyte*)rawData);

    // Clean up
    env->ReleaseStringUTFChars(src_ip, srcIp);
    env->ReleaseStringUTFChars(dst_ip, dstIp);
    env->ReleaseByteArrayElements(payload, payloadBytes, JNI_ABORT);

    return result;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_prashik_firewallapp_NativeBridge_extractTcpPayload(
        JNIEnv *env,
        jobject thiz,
        jbyteArray packetData,
        jint length
) {
    jbyte* rawBytes = env->GetByteArrayElements(packetData, nullptr);
    if (rawBytes == nullptr) return nullptr;

    timeval time{};
    gettimeofday(&time, nullptr);

    pcpp::RawPacket rawPacket(reinterpret_cast<const uint8_t*>(rawBytes), length, time, false, pcpp::LINKTYPE_RAW);
    pcpp::Packet parsedPacket(&rawPacket);

    env->ReleaseByteArrayElements(packetData, rawBytes, JNI_ABORT);

    auto tcpLayer = parsedPacket.getLayerOfType<pcpp::TcpLayer>();
    if (tcpLayer == nullptr) return nullptr;

    pcpp::Layer* nextLayer = tcpLayer->getNextLayer();
    auto payloadLayer = dynamic_cast<pcpp::PayloadLayer*>(nextLayer);

    if (payloadLayer == nullptr) return nullptr;

    const uint8_t* payloadData = payloadLayer->getPayload();
    size_t payloadLen = payloadLayer->getPayloadLen();

    if (payloadData == nullptr || payloadLen == 0) return nullptr;

    jbyteArray result = env->NewByteArray(payloadLen);
    env->SetByteArrayRegion(result, 0, payloadLen, reinterpret_cast<const jbyte*>(payloadData));

    return result;
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_prashik_firewallapp_NativeBridge_buildTcpResponsePacket(
        JNIEnv *env,
        jobject thiz,
        jbyteArray payload,
        jstring src_ip,
        jint src_port,
        jstring dst_ip,
        jint dst_port
) {
    // Extract input parameters
    const char *srcIpStr = env->GetStringUTFChars(src_ip, nullptr);
    const char *dstIpStr = env->GetStringUTFChars(dst_ip, nullptr);
    jbyte *payloadData = env->GetByteArrayElements(payload, nullptr);
    jsize payloadLen = env->GetArrayLength(payload);

    // Create IP/TCP layers
    pcpp::IPv4Address srcIp(srcIpStr);
    pcpp::IPv4Address dstIp(dstIpStr);

    pcpp::EthLayer ethLayer(
            pcpp::MacAddress("aa:aa:aa:aa:aa:aa"),
            pcpp::MacAddress("bb:bb:bb:bb:bb:bb"),
            PCPP_ETHERTYPE_IP
    );

    pcpp::IPv4Layer ipLayer(srcIp, dstIp);
    ipLayer.getIPv4Header()->protocol = pcpp::PACKETPP_IPPROTO_TCP;

    pcpp::TcpLayer tcpLayer(src_port, dst_port);
    tcpLayer.getTcpHeader()->ackFlag = 1;
    tcpLayer.getTcpHeader()->pshFlag = 1;
    tcpLayer.getTcpHeader()->ackNumber = htonl(1);  // adjust if needed
    tcpLayer.getTcpHeader()->sequenceNumber = htonl(1); // adjust if needed

    pcpp::PayloadLayer payloadLayer(
            reinterpret_cast<uint8_t *>(payloadData),
            payloadLen
    );

    // Build the full packet
    pcpp::Packet responsePacket(1);
    responsePacket.addLayer(&ethLayer);
    responsePacket.addLayer(&ipLayer);
    responsePacket.addLayer(&tcpLayer);
    responsePacket.addLayer(&payloadLayer);

    responsePacket.computeCalculateFields();

    // Convert to byte array for Java
    int totalLen = responsePacket.getRawPacket()->getRawDataLen();
    const uint8_t *rawData = responsePacket.getRawPacket()->getRawData();

    jbyteArray result = env->NewByteArray(totalLen);
    env->SetByteArrayRegion(result, 0, totalLen, reinterpret_cast<const jbyte *>(rawData));

    // Cleanup
    env->ReleaseByteArrayElements(payload, payloadData, JNI_ABORT);
    env->ReleaseStringUTFChars(src_ip, srcIpStr);
    env->ReleaseStringUTFChars(dst_ip, dstIpStr);

    return result;
}