package xyz.chener.ext.napt.server.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.server.Main;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;

import java.io.FileInputStream;
import java.util.Properties;


@Slf4j
public class ServerNettyMain {

    public static final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    public static final NioEventLoopGroup workGroup = new NioEventLoopGroup(10);

    public void start(){
        Thread t = new Thread(() -> {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(@NotNull SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            pipeline.addLast(new ProtobufVarint32FrameDecoder());
                            pipeline.addLast(new ProtobufDecoder(DataFrameEntity.DataFrame.getDefaultInstance()));
                            pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                            pipeline.addLast(new ProtobufEncoder());
                            pipeline.addLast( new DataForwardHandle());
                        }
                    })
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            try {
                ChannelFuture future = bootstrap.bind(Integer.parseInt(Continer.get(ConfigLoader.class).get(ConfigLoader.KeyEnum.PORT))).sync();
                log.info("启动服务器");
                future.channel().closeFuture().sync();
            }catch (Exception exception){
                Main.stop(String.format("转发线程终止，原因：%s", exception.getMessage()));
            }
        });

        t.setName("服务端数据转发处理线程");
        t.start();
    }

    public void stop(){
        bossGroup.shutdownGracefully();
        workGroup.shutdownGracefully();
    }



}
