package xyz.chener.ext.napt.client.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;
import xyz.chener.ext.napt.client.entity.DataFrameEntity;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: chenzp
 * @Date: 2023/05/23/15:53
 * @Email: chen@chener.xyz
 */


@Slf4j
public class ClientCore {

    private EventLoopGroup group = new NioEventLoopGroup(10);


    private final Thread thread ;

    private volatile Channel channel = null;

    private ClientDataHandle clientDataHandle;

    public ClientCore(){
        thread = new Thread(this::run);
        thread.setName("ClientCoreThread");
        thread.start();
    }

    public void stop(){
        thread.interrupt();
        if (channel != null)
            channel.close();
        group.shutdownGracefully();
    }

    public void sendToServer(DataFrameEntity.DataFrame dataFrame){
        if (channel != null){
            channel.writeAndFlush(dataFrame);
        }
    }


    private void run(){
        while (!Thread.currentThread().isInterrupted()){
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .channel(io.netty.channel.socket.nio.NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            Boolean forceAccess = Boolean.parseBoolean(Continer.get(ConfigLoader.class).get(ConfigLoader.KeyEnum.FORCE_ACCESS));
                            String uid = Continer.get(ConfigLoader.class).get(ConfigLoader.KeyEnum.UID);
                            clientDataHandle = new ClientDataHandle(forceAccess,uid);

                            ChannelPipeline pipeline = socketChannel.pipeline();
                            pipeline.addLast(new ProtobufVarint32FrameDecoder());
                            pipeline.addLast(new ProtobufDecoder(DataFrameEntity.DataFrame.getDefaultInstance()));
                            pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
                            pipeline.addLast(new ProtobufEncoder());
                            pipeline.addLast(clientDataHandle);
                        }
                    });

            try {
                String host = Continer.get(ConfigLoader.class).get(ConfigLoader.KeyEnum.HOST);
                String port = Continer.get(ConfigLoader.class).get(ConfigLoader.KeyEnum.PORT);
                ChannelFuture future = bootstrap.connect(host,Integer.parseInt(port)).sync();
                channel = future.channel();
                log.info("连接服务器成功");
                future.channel().closeFuture().sync();
            } catch (Exception e) {
                if (e instanceof InterruptedException){
                    Thread.currentThread().interrupt();
                }
                log.error(e.getMessage());
            } finally {
                channel = null;
                onclose();
                log.info("断开与服务器的连接");
                if (!Thread.currentThread().isInterrupted()){
                    try {
                        log.info("尝试重新连接服务器...");
                        Thread.sleep(5000);
                    } catch (InterruptedException e) { }
                }
            }
        }
    }

    private void onclose(){
        // 关闭本地已建立连接的Addr
        clientDataHandle.stopAllRemoteRequest();
    }

}
