package xyz.chener.napt.common.utils

import io.netty.channel.socket.DatagramPacket

class UdpUtils {
    companion object {

        fun readDatagramPacketData(datagramPacket: DatagramPacket): ByteArray {
            val bytes = ByteArray(datagramPacket.content().readableBytes())
            datagramPacket.content().readBytes(bytes)
            return bytes
        }

    }
}