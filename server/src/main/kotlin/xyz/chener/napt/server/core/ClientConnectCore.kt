package xyz.chener.napt.server.core

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component
import xyz.chener.napt.common.entity.DataFrameEntity


/**
 * 客户端通信netty核心类
 */

@Component
open class ClientConnectCore : CommandLineRunner,ApplicationListener<ContextClosedEvent> {

    private val log : Logger = LoggerFactory.getLogger(ClientConnectCore::class.java)

    private val bossGroup = NioEventLoopGroup(1)

    private val workGroup = NioEventLoopGroup(5)

    @Value("\${napt.server.port:5900}")
    private var port:Int = 5900

    var channel:Channel? = null


    @Autowired
    lateinit var clientManager: ClientManager

    @Autowired
    lateinit var messageHandle: MessageHandle


    fun start(){
        Thread.ofVirtual().name("ClientConnectCore").start(this::nettyThread)
    }

    fun stop(){
        channel?.close()
    }


    private fun nettyThread(){
        val bootstrap : ServerBootstrap = ServerBootstrap()
            .group(bossGroup, workGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(socketChannel: SocketChannel) {
                    val pipeline = socketChannel.pipeline()
                    pipeline.addLast(ProtobufVarint32FrameDecoder())
                    pipeline.addLast(ProtobufDecoder(DataFrameEntity.DataFrame.getDefaultInstance()))
                    pipeline.addLast(ProtobufVarint32LengthFieldPrepender())
                    pipeline.addLast(ProtobufEncoder())
                    pipeline.addLast(ClientMessageDispatch(clientManager,messageHandle))
                }
            })
            .childOption(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
        val future = bootstrap.bind(port).sync()
        log.info("Client Connect Core Start Success.")
        channel = future.channel()
        channel?.closeFuture()?.sync()
    }

    override fun run(vararg args: String?) {
        start()
    }

    override fun onApplicationEvent(event: ContextClosedEvent) {
        log.info("Client Connect Core Stop.")
        stop()
    }


}