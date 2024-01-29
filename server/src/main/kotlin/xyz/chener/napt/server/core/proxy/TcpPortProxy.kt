package xyz.chener.napt.server.core.proxy

import com.google.protobuf.ByteString
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.bytes.ByteArrayDecoder
import io.netty.handler.codec.bytes.ByteArrayEncoder
import io.netty.handler.traffic.GlobalTrafficShapingHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import xyz.chener.napt.common.entity.ProxyType
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class TcpPortProxy(proxyType: ProxyType, clientUid: String, port: Int, clientAddress: String, speedLimit: Int) :
    AbstractPortProxy(proxyType, clientUid, port, clientAddress) {

    init {
        if (speedLimit == -1) {
            this.speedLimit = Int.MAX_VALUE
        } else {
            this.speedLimit = speedLimit
        }
        isStart = true
        thread = Thread.ofVirtual().name("Tcp-$port").start(this::run)
    }


    private val log: Logger = LoggerFactory.getLogger(TcpPortProxy::class.java)


    //连接到代理端口的客户端
    private val channelIdToContext: ConcurrentHashMap<String, ChannelHandlerContext> = ConcurrentHashMap()


    override fun run() {
        while (isStart && thread?.isInterrupted == false) {

            try {
                val bootstrap = ServerBootstrap()
                bootstrap.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .childHandler(tcpPortProxyInitializer())
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .bind(port).sync().let {
                        channel = it.channel()
                        channel!!.closeFuture().sync()
                    }
            } catch (e: Exception) {
                if (e is InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                channel = null

                if (isStart) {
                    log.error("TcpPortProxy ${clientUid}:${port} run error: ", e)
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
                    log.info("TcpPortProxy ${clientUid}:${port} restart after 5s")
                    try {
                        Thread.sleep(5000)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }

            } finally {
                channel = null
            }

        }
    }

    // 后端客户端发送来数据 转发至远程客户端
    fun sendToClient(remoteChannelId: String, data: ByteArray) {
        channelIdToContext[remoteChannelId]?.channel()?.writeAndFlush(data)
    }

    // 后端客户端要求关闭远程客户端
    fun closeRemoteChannel(remoteChannelId: String) {
        channelIdToContext[remoteChannelId]?.channel()?.close()
    }

    private fun tcpPortProxyInitializer() : ChannelInitializer<SocketChannel> {
        return object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(socketChannel: SocketChannel) {
                val globalTrafficShapingHandler = GlobalTrafficShapingHandler(socketChannel.eventLoop(), speedLimit.toLong(), speedLimit.toLong())

                val pipeline = socketChannel.pipeline()
                pipeline.addLast(globalTrafficShapingHandler)
                pipeline.addLast( ByteArrayEncoder())
                pipeline.addLast(ByteArrayDecoder())
                pipeline.addLast(tcpPortProxyHandler())
            }
        }
    }

    fun tcpPortProxyHandler(): ChannelInboundHandlerAdapter {
        return object : ChannelInboundHandlerAdapter() {

            // 接受到的客户端数据发送到后台客户端
            fun sendToBackendClient(data: ByteArray?, ctx: ChannelHandlerContext) {
                getClientManager().clientUidToClientChannelId[clientUid]?.let {channelId ->
                    getClientManager().channelIdToChannel[channelId]?.let { channelHandlerContext ->
                        // 流量计数器
                        if (!getTrafficLimiter().check(clientUid,clientAddress,port,data?.size ?: 0)) {
                            return
                        }

                        val dataFrame: DataFrameEntity.DataFrame = DataFrameEntity.DataFrame.newBuilder()
                            .setCode(DataFrameCode.REMOTE_CHANNEL_ACCEPT_TCP.code)
                            .setClientAddress(clientAddress)
                            .setData(ByteString.copyFrom(Optional.ofNullable<ByteArray>(data).orElse(ByteArray(0))))
                            .setTcpRemoteChannelId(ctx.channel().id().asLongText())
                            .setRequestNtType(proxyType.code)
                            .build()
                        channelHandlerContext.channel().writeAndFlush(dataFrame)
                    }

                }
            }

            private fun doClose(ctx: ChannelHandlerContext) {
                kotlin.runCatching {
                    val channelId = ctx.channel().id().asLongText()
                    channelIdToContext.remove(channelId)

                    getClientManager().clientUidToClientChannelId[clientUid]?.let{backendChannelId ->
                        getClientManager().channelIdToChannel[backendChannelId]?.let { backendChannelHandlerContext ->
                            val dataFrame = DataFrameEntity.DataFrame.newBuilder()
                                .setCode(DataFrameCode.REMOTE_CHANNEL_CLOSE_TCP.code)
                                .setTcpRemoteChannelId(ctx.channel().id().asLongText()).build()
                            backendChannelHandlerContext.channel().writeAndFlush(dataFrame)
                        }
                    }
                }
            }


            override fun channelActive(ctx: ChannelHandlerContext) {
                channelIdToContext[ctx.channel().id().asLongText()] = ctx
                sendToBackendClient(null, ctx)
            }

            override fun channelInactive(ctx: ChannelHandlerContext) {
                doClose(ctx)
            }

            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                log.error("TcpPortProxyHandler exceptionCaught: ", cause)
                doClose(ctx)
                ctx.channel().close()
            }

            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                if (msg is ByteArray) {
                    sendToBackendClient(msg, ctx)
                }
            }
        }
    }
}