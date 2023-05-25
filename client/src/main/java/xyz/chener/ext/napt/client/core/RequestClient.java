package xyz.chener.ext.napt.client.core;

import com.google.protobuf.ByteString;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import lombok.extern.slf4j.Slf4j;
import xyz.chener.ext.napt.client.entity.DataFrameCode;
import xyz.chener.ext.napt.client.entity.DataFrameEntity;

import java.util.Objects;
import java.util.Optional;

/**
 * @Author: chenzp
 * @Date: 2023/05/24/10:12
 * @Email: chen@chener.xyz
 */

@Slf4j
public class RequestClient {
    private static final EventLoopGroup group = new NioEventLoopGroup(10);

    private final String clientAddr;
    private final String clientHost;

    private final int clientPort;

    private final String remoteChannelId;

    private final byte[] initData;

    private final Thread thread;

    private Channel channel = null;

    public RequestClient(String clientAddr, String clientHost, int clientPort, String remoteChannelId, byte[] initData) {
        this.clientAddr = clientAddr;
        this.clientHost = clientHost;
        this.clientPort = clientPort;
        this.remoteChannelId = remoteChannelId;
        this.initData = initData;
        thread = new Thread(this::run);
        thread.start();
    }

    public String getClientAddr() {
        return clientAddr;
    }

    public int getClientPort() {
        return clientPort;
    }

    public String getRemoteChannelId() {
        return remoteChannelId;
    }

    public Channel getChannel() {
        return channel;
    }

    public void sendData(byte[] data) {
        if (Objects.nonNull(this.channel))
        {
            if (Objects.nonNull(data))
                channel.writeAndFlush(data);
        }else {
            try {
                for (int i = 0; i < 20; i++) {
                    Thread.sleep(100);
                    if (Objects.nonNull(this.channel))
                    {
                        channel.writeAndFlush(data);
                        return;
                    }
                }
            }catch (Exception ignored) { }
            log.error("send data error,channel is null");
        }
    }

    public void stop() {
        log.info("远程 channel [{}] 与 [{}] 断开连接", remoteChannelId, clientAddr);
        if (Objects.nonNull(channel)) {
            channel.close();
        }
        thread.interrupt();
    }

    private void sendClientClose(){
        DataFrameEntity.DataFrame dataFrame = DataFrameEntity.DataFrame.newBuilder()
                .setCode(DataFrameCode.CLIENT_CLOSE_REMOTE_CHANNEL)
                .setRemoteChannelId(remoteChannelId)
                .setMessage(clientAddr)
                .build();
        Continer.get(ClientCore.class).sendToServer(dataFrame);
    }

    private void run() {
        log.info("远程 channel [{}] 试图与 [{}] 建立连接", remoteChannelId, clientAddr);
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .option(ChannelOption.TCP_NODELAY, true)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline p = socketChannel.pipeline();
                        p.addLast(new ByteArrayDecoder());
                        p.addLast(new ByteArrayEncoder());
                        p.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                log.info("远程 channel [{}] 与 [{}] 建立连接", remoteChannelId, clientAddr);
                                if (Objects.nonNull(initData)) {
                                    ctx.channel().writeAndFlush(initData);
                                }
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                sendClientClose();
                            }

                            @Override
                            public void channelRead( ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof byte[] b) {
                                    DataFrameEntity.DataFrame dataFrame = DataFrameEntity.DataFrame.newBuilder()
                                            .setCode(DataFrameCode.CLIENT_CHANNEL_ACCEPT)
                                            .setRemoteChannelId(remoteChannelId)
                                            .setMessage(clientAddr)
                                            .setData(ByteString.copyFrom(Optional.ofNullable(b).orElse(new byte[0])))
                                            .build();
                                    Continer.get(ClientCore.class).sendToServer(dataFrame);
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                sendClientClose();
                                ctx.channel().close();
                                log.error("远程 channel [{}] 与 [{}] 客户端地址传输异常 : {}", remoteChannelId, clientAddr, cause.getMessage());
                            }
                        });
                    }
                });

        try {
            ChannelFuture future = bootstrap.connect(clientHost, clientPort).sync();
            channel = future.channel();
            channel.closeFuture().sync();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            } else {
                log.error("与映射地址连接断开 : {}",e.getMessage());
                sendClientClose();
            }
        } finally {
            channel = null;
        }
    }

}
