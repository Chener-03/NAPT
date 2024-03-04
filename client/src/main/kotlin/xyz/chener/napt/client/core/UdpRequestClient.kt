package xyz.chener.napt.client.core

import com.google.protobuf.ByteString
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.chener.napt.client.ApplicationContextHolder
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import xyz.chener.napt.common.entity.ProxyType
import xyz.chener.napt.common.utils.UdpUtils
import java.net.InetSocketAddress
import java.util.*
import kotlin.concurrent.Volatile

class UdpRequestClient(val clientAddress:String,val  clientHost:String, val clientPort:Int, val remoteHost:String,val remotePort:Int, val initData:ByteArray) {

    private val log: Logger = LoggerFactory.getLogger(UdpRequestClient::class.java)

    private var thread: Thread? = null

    private var cleanThread: Thread? = null

    private var lastActiveTime = 0L

    @Volatile
    private var isStart = false

    private var channel: Channel? = null


    @kotlin.jvm.Volatile
    private var serverConnectCore: ServerConnectCore? = null

    private fun getServerConnectCore(): ServerConnectCore {
        if (serverConnectCore == null) {
            synchronized(this) {
                if (serverConnectCore == null) {
                    serverConnectCore = ApplicationContextHolder.applicationContext.getBean(ServerConnectCore::class.java)
                }
            }
        }
        return serverConnectCore!!
    }


    init {
        isStart = true
        lastActiveTime = System.currentTimeMillis()
        thread = Thread.ofVirtual().name("UdpReq:${clientAddress}").start(this::run)
        cleanThread = Thread.ofVirtual().name("UdpClr:${clientAddress}").start(this::clear)
    }

    private fun run(){
        log.info("UDP:远程 address [{}:{}] 正在与 [{}] 建立连接", remoteHost,remotePort, clientAddress)
        val bootstrap = Bootstrap()
            .group(ServerConnectCore.group)
            .channel(NioDatagramChannel::class.java)
            .option(ChannelOption.SO_BROADCAST, true)
            .handler(udpRequestInitializer())

        try {
            val channelFuture = bootstrap.connect(clientHost, clientPort).sync()
            channel = channelFuture.channel()
            channel?.closeFuture()?.sync()
        } catch (e:Exception){
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }finally {
            close()
        }

    }

    // 写到 后端客户端
    fun writeAndFlush(data: ByteArray){
        lastActiveTime = System.currentTimeMillis()
        val addr = InetSocketAddress(clientHost, clientPort)
        channel?.writeAndFlush(DatagramPacket(Unpooled.copiedBuffer(data), addr))
    }


    fun close(){
        isStart = false
        channel?.close()
        getServerConnectCore().udpRemoteRequestList.remove(this)
        thread?.interrupt()
        cleanThread?.interrupt()
    }


    private fun clear(){
        while (isStart && !Thread.currentThread().isInterrupted) {
            try {
                Thread.sleep(2000)
                if (System.currentTimeMillis() - lastActiveTime > 1000L * 60 * 5) {
                    close()
                    return
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }


    private fun udpRequestInitializer(): ChannelInitializer<NioDatagramChannel> {
        return object : ChannelInitializer<NioDatagramChannel>() {
            @Throws(Exception::class)
            override fun initChannel(ch: NioDatagramChannel) {
                ch.pipeline().addLast(udpRequestHandler())
            }
        }
    }

    private fun udpRequestHandler() : ChannelInboundHandlerAdapter {
        return object : ChannelInboundHandlerAdapter() {
            override fun channelActive(ctx: ChannelHandlerContext) {
                writeAndFlush(initData)
            }

            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                lastActiveTime = System.currentTimeMillis()
                if (msg is DatagramPacket){
                    try {
                        UdpUtils.readDatagramPacketData(msg).let {
                            val dataFrame: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
                                .setCode(DataFrameCode.CLIENT_CHANNEL_ACCEPT_UDP.code)
                                .setUdpRemoteIp(remoteHost)
                                .setUdpRemotePort(remotePort)
                                .setClientAddress(clientAddress)
                                .setRequestNtType(ProxyType.UDP.code)
                                .setData(ByteString.copyFrom(Optional.ofNullable<ByteArray>(it).orElse(ByteArray(0))))
                                .build()
                            getServerConnectCore().sentDataFrameToServer(dataFrame)
                        }
                    }finally {
                        msg.release()
                    }
                }
            }


            override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                log.error("UDP:远程 address [{}:{}] 与 [{}] 连接异常", remoteHost,remotePort, clientAddress, cause)
            }
        }
    }


    override fun equals(other: Any?): Boolean {
        if (other is UdpRequestClient) {
            return (other.remoteHost == this.remoteHost) && other.remotePort == this.remotePort
        }
        return super.equals(other)
    }
}