package xyz.chener.napt.client.core

import com.google.protobuf.ByteString
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.chener.napt.client.ApplicationContextHolder
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import xyz.chener.napt.common.entity.ProxyType


class TcpRequestClient(val clientAddress:String,val  clientHost:String, val clientPort:Int, val remoteChannelId:String, val initData:ByteArray) {

    private val log: Logger = LoggerFactory.getLogger(TcpRequestClient::class.java)

    private val thread: Thread? = null

    private var channel: Channel? = null

    @Volatile
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
        Thread.ofVirtual().name("TcpReq:${clientAddress}").start(this::run)
    }

    private fun run() {
        log.info("TCP:远程 channel [{}] 正在与 [{}] 建立连接", remoteChannelId, clientAddress)
        var bootstrap = Bootstrap()
            .group(ServerConnectCore.group)
            .option(ChannelOption.TCP_NODELAY, true)
            .channel(NioSocketChannel::class.java)
            .handler(tcpRequestInitializer())
        try {
            val future = bootstrap.connect(clientHost, clientPort).sync()
            channel = future.channel()
            channel?.closeFuture()?.sync()
        }catch (e:Exception){
            if (e is InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }finally {
            log.error("TCP:远程 channel [{}] 与 [{}] 断开连接", remoteChannelId, clientAddress)
            notifyRemoteChannelClose()
            close()
        }

    }


    fun writeAndFlush(data: ByteArray) {
        channel?.writeAndFlush(data)
    }

    fun close() {
        getServerConnectCore().tcpRemoteRequestMap.remove(remoteChannelId)
        channel?.close()
        thread?.interrupt()
    }

    private fun notifyRemoteChannelClose(){
        kotlin.runCatching {
            val dataFrame: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
                .setCode(DataFrameCode.CLIENT_CLOSE_REMOTE_CHANNEL_TCP.code)
                .setTcpRemoteChannelId(remoteChannelId)
                .setClientAddress(clientAddress)
                .setRequestNtType(ProxyType.TCP.code)
                .build()
            getServerConnectCore().sentDataFrameToServer(dataFrame)
        }
    }

    private fun tcpRequestInitializer(): ChannelInitializer<SocketChannel> {
        return object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                val p: ChannelPipeline = ch.pipeline()
                p.addLast(ByteArrayDecoder())
                p.addLast(ByteArrayEncoder())
                p.addLast(tcpRequestHandler())
            }
        }
    }


    private fun tcpRequestHandler(): ChannelInboundHandlerAdapter {
        return object : ChannelInboundHandlerAdapter() {


            override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                ctx?.channel()?.close()
            }

            override fun channelActive(ctx: ChannelHandlerContext) {
                log.info("TCP:远程 channel [{}] 与 [{}] 连接成功", remoteChannelId, clientAddress)
                ctx.channel().writeAndFlush(initData)
            }

            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                if (msg is ByteArray) {
                    val dataFrame: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.CLIENT_CHANNEL_ACCEPT_TCP.code)
                        .setTcpRemoteChannelId(remoteChannelId)
                        .setClientAddress(clientAddress)
                        .setRequestNtType(ProxyType.TCP.code)
                        .setData(ByteString.copyFrom(msg))
                        .build()
                    getServerConnectCore().sentDataFrameToServer(dataFrame)
                }
            }
        }
    }

}