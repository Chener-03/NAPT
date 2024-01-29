package xyz.chener.napt.client.core

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component
import xyz.chener.napt.common.entity.DataFrameCode
import xyz.chener.napt.common.entity.DataFrameEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.Volatile


@Component
open class ServerConnectCore : CommandLineRunner, ApplicationListener<ContextClosedEvent>{

    companion object {
        val group: EventLoopGroup = NioEventLoopGroup(10)
    }

    private val log: Logger = LoggerFactory.getLogger(ServerConnectCore::class.java)

    @Volatile
    private var start : Boolean = false

    @Volatile
    private var channel: Channel? = null

    @Value("\${napt.client.server-address}")
    private lateinit var serverAddress: String

    @Value("\${napt.client.server-port}")
    private var serverPort: Int = 5900

    @Value("\${napt.client.client-uid}")
    private lateinit var clientUid: String

    @Value("\${napt.client.force-access}")
    private var isForce: Boolean = false



    // 对应 TCP 远程 channelID 和 本地 TcpRequestClient  缓存
    val tcpRemoteRequestMap: MutableMap<String, TcpRequestClient> = ConcurrentHashMap()
    // 本地 UDP 缓存
    val udpRemoteRequestList: MutableList<UdpRequestClient> = CopyOnWriteArrayList()


    private var thread: Thread? = null

    private var heartBeatThread: Thread? = null

    override fun run(vararg args: String?) {
        start = true
        thread = Thread.ofPlatform().name("ServerConnectCore").start(this::start)
        heartBeatThread = Thread.ofVirtual().name("HeartBeat").start(this::heartBeat)
    }

    override fun onApplicationEvent(event: ContextClosedEvent) {
        log.info("Exit ... ")
        start = false
        thread?.interrupt()
        heartBeatThread?.interrupt()
        channel?.close()
        group.shutdownGracefully()
    }

    fun start(){
        log.info("Internal network penetration Start ... ")
        while (start && !Thread.currentThread().isInterrupted){
            try {
                log.info("Connect to server ... ")
                val bootstrap = Bootstrap()
                    .group(group)
                    .option<Boolean>(ChannelOption.TCP_NODELAY, true)
                    .channel(NioSocketChannel::class.java)
                    .handler(object : ChannelInitializer<SocketChannel>() {
                        @Throws(java.lang.Exception::class)
                        override fun initChannel(socketChannel: SocketChannel) {
                            val pipeline = socketChannel.pipeline()
                            pipeline.addLast(ProtobufVarint32FrameDecoder())
                            pipeline.addLast(ProtobufDecoder(DataFrameEntity.DataFrame.getDefaultInstance()))
                            pipeline.addLast(ProtobufVarint32LengthFieldPrepender())
                            pipeline.addLast(ProtobufEncoder())
                            pipeline.addLast(ServerDataHandle(clientUid, isForce,tcpRemoteRequestMap,udpRemoteRequestList))
                        }
                    })

                val future = bootstrap.connect(serverAddress, serverPort).sync()
                channel = future.channel()
                log.info("Connect to server success ... ")
                channel!!.closeFuture().sync()
            }catch (e: Exception){
                if (e is InterruptedException){
                    Thread.interrupted()
                }else {
                    log.error("Connect to server error ,reason {} ", e.message)
                }
            } finally {
                log.info("Connect to server closed ... ")
                if (!Thread.currentThread().isInterrupted){
                    kotlin.runCatching {
                        log.info("Reconnect to server after 5 seconds ... ")
                        Thread.sleep(1000 * 5)
                    }
                }
            }
        }
    }

    fun heartBeat(){
        while (start && !Thread.currentThread().isInterrupted){
            try {
                Thread.sleep(1000 * 30)
                // 心跳
                if (channel != null && channel!!.isActive){
                    val data: DataFrameEntity.DataFrame =
                        DataFrameEntity.DataFrame.newBuilder().setCode(DataFrameCode.HEART_BEAT.code).build()
                    channel!!.writeAndFlush(data)
                }
            }catch (e: Exception){
                if (e is InterruptedException){
                    Thread.interrupted()
                }
            }
        }

    }

    fun sentDataFrameToServer(data: DataFrameEntity.DataFrame){
        channel?.writeAndFlush(data)
    }

}