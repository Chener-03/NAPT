package xyz.chener.napt.server.core.proxy

import com.google.protobuf.ByteString
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.traffic.GlobalTrafficShapingHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import xyz.chener.napt.common.entity.ProxyType
import xyz.chener.napt.common.utils.UdpUtils
import java.net.InetSocketAddress
import java.util.*

class UdpPortProxy(proxyType: ProxyType, clientUid: String, port: Int, clientAddress: String, speedLimit: Int) :
    AbstractPortProxy(proxyType, clientUid, port, clientAddress) {

    init {
        if (speedLimit == -1) {
            this.speedLimit = Int.MAX_VALUE
        } else {
            this.speedLimit = speedLimit
        }
        isStart = true
        this.thread = Thread.ofVirtual().name("Udp-$port").start { this.run() }
    }


    private val log: Logger = LoggerFactory.getLogger(UdpPortProxy::class.java)



    override fun run() {
        while (isStart && thread?.isInterrupted == false) {
            try {
                val bootstrap = Bootstrap()
                bootstrap.group(workGroup)
                    .channel(NioDatagramChannel::class.java)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(udpPortProxyInitializer())

                    .bind(port).sync().let {
                        channel = it.channel()
                        channel!!.closeFuture().sync()
                    }
            }catch (e:Exception){
                if (e is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                channel = null
                if (isStart) {
                    log.error("UdpPortProxy ${clientUid}:${port} run error: ", e)
                    getClientManager().clientUidToClientChannelId[clientUid]?.let {channelId ->
                        getClientManager().channelIdToChannel[channelId]?.let { channelHandlerContext ->
                            val dataFrame: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
                                .setCode(DataFrameCode.REMOTE_PORT_START_ERROR.code)
                                .setClientAddress(clientAddress)
                                .setMessage("${DataFrameCode.REMOTE_PORT_START_ERROR.message} : $port : ${e.message}")
                                .setRequestNtType(proxyType.code)
                                .build()
                            channelHandlerContext.channel().writeAndFlush(dataFrame)
                        }
                    }
                    log.info("UdpPortProxy ${clientUid}:${port} restart after 5s")
                    try {
                        Thread.sleep(5000)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            } finally {
                channel = null
            }
        }
    }


    // 后端客户端发送来数据 转发至远程客户端
    fun sendToClient(remoteIp:String,remotePort:Int,data:ByteArray){
        val addr: InetSocketAddress = InetSocketAddress(remoteIp, remotePort)
        channel?.writeAndFlush(DatagramPacket(Unpooled.copiedBuffer(data),addr))
    }


    private fun udpPortProxyInitializer() : ChannelInitializer<NioDatagramChannel> {
        return object : ChannelInitializer<NioDatagramChannel>() {
            override fun initChannel(socketChannel: NioDatagramChannel) {
                val globalTrafficShapingHandler = GlobalTrafficShapingHandler(socketChannel.eventLoop(), speedLimit.toLong(), speedLimit.toLong())
                val p: ChannelPipeline = socketChannel.pipeline()
                p.addLast(globalTrafficShapingHandler)
                p.addLast(udpPortProxyHandler())
            }
        }
    }



    fun udpPortProxyHandler(): ChannelInboundHandlerAdapter{
        return object : ChannelInboundHandlerAdapter() {

            // 接受到的客户端数据发送到后台客户端
            fun sendToBackendClient(data: ByteArray?, address: InetSocketAddress){
                getClientManager().clientUidToClientChannelId[clientUid]?.let {channelId ->
                    getClientManager().channelIdToChannel[channelId]?.let { channelHandlerContext ->
                        // 流量计数器
                        if (!getTrafficLimiter().check(clientUid,clientAddress,port,data?.size ?: 0)) {
                            return
                        }

                        val dataFrame: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
                            .setCode(DataFrameCode.REMOTE_CHANNEL_ACCEPT_UDP.code)
                            .setClientAddress(clientAddress)
                            .setData(ByteString.copyFrom(Optional.ofNullable<ByteArray>(data).orElse(ByteArray(0))))
                            .setUdpRemoteIp(address.address.hostAddress)
                            .setUdpRemotePort(address.port)
                            .setRequestNtType(proxyType.code)
                            .build()
                        channelHandlerContext.channel().writeAndFlush(dataFrame)
                    }

                }
            }

            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                if (msg is DatagramPacket) {
                    try {
                        val btt: ByteArray = UdpUtils.readDatagramPacketData(msg)
                        sendToBackendClient(btt, msg.sender())
                    } finally {
                        msg.release()
                    }
                }
            }
        }
    }

}