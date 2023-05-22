package xyz.chener.ext.napt.server.core;

import com.google.protobuf.ByteString;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.server.entity.DataFrameCode;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 对应每个端口的转发服务
 */
@Slf4j
public class RequestNt {

    private final String clientUid;
    private final Integer port;
    private final String clientAddr;

    private Thread thread;

    private Channel channel = null;

    private volatile boolean isStart = true;

    private final Lock lock = new ReentrantLock();

    // 存放当前端口连接通道
    private final ConcurrentHashMap<String, ChannelHandlerContext> map = new ConcurrentHashMap<>();

    public String getClientAddr() {
        return clientAddr;
    }

    public String getClientUid() {
        return clientUid;
    }

    public Integer getPort() {
        return port;
    }

    public RequestNt(String clientUid, Integer port, String clientAddr) {
        this.clientUid = clientUid;
        this.port = port;
        this.clientAddr = clientAddr;
        isStart = true;
        thread = new Thread(this::run);
        thread.start();
    }

    public void stop()
    {
        lock.lock();
        try {
            isStart = false;
            if (Objects.nonNull(channel))
                channel.close();
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException ignored) {}
        }finally {
            lock.unlock();
        }
    }

    public void write(String remoteChannelId,byte[] data)
    {
        ChannelHandlerContext channelHandlerContext = map.get(remoteChannelId);
        if (Objects.nonNull(channelHandlerContext))
            channelHandlerContext.channel().writeAndFlush(data);
    }

    private void run()
    {
        while (isStart)
        {
            try {
                ServerBootstrap bootstrap = new ServerBootstrap()
                        .group(ServerNettyMain.bossGroup, ServerNettyMain.workGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) throws Exception {
                                ChannelPipeline p = socketChannel.pipeline();
                                p.addLast(new ByteArrayEncoder());
                                p.addLast(new ByteArrayDecoder());
                                p.addLast(new OnePortForwardHandle(clientUid,clientAddr,map,port));
                            }
                        })
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.SO_BACKLOG, 128)
                        .childOption(ChannelOption.SO_KEEPALIVE, true);
                ChannelFuture future = bootstrap.bind(this.port).sync();
                channel = future.channel();
                future.channel().closeFuture().sync();
            }catch (Exception exception)
            {
                channel = null;
                if (isStart)
                {
                    log.info("绑定端口异常,即将重试,clientUID:{},port:{},exception:{}",clientUid,port,exception.getMessage());
                    String channelId = ConnectCache.clientChannel.get(clientUid);
                    ChannelHandlerContext context = null;
                    if (channelId != null && (context=ConnectCache.channelMap.get(channelId))!=null){
                        DataFrameEntity.DataFrame dataFrame = DataFrameEntity.DataFrame.newBuilder()
                                .setCode(DataFrameCode.REMOTE_PORT_START_ERROR)
                                .setMessage(String.format("服务器绑定端口异常,即将重试,clientUID:%s,port:%s,exception:%s",clientUid,port,exception.getMessage()))
                                .setRemoteChannelId(channelId).build();
                        context.channel().writeAndFlush(dataFrame);
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) {}
                }
            }
            channel = null;
        }
    }


    private static class OnePortForwardHandle extends ChannelInboundHandlerAdapter{

        private final String clientUid;
        private final String clientAddr;
        private final Map<String, ChannelHandlerContext> map;

        private final int port;

        private OnePortForwardHandle(String clientUid, String clientAddr, Map<String, ChannelHandlerContext> map,int port) {
            this.clientUid = clientUid;
            this.clientAddr = clientAddr;
            this.map = map;
            this.port = port;
        }

        private void doClose(ChannelHandlerContext ctx) {
            map.remove(ctx.channel().id().asLongText());
            String channelId = ConnectCache.clientChannel.get(clientUid);
            ChannelHandlerContext context = null;
            if (channelId != null && (context=ConnectCache.channelMap.get(channelId))!=null){
                DataFrameEntity.DataFrame dataFrame = DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.REMOTE_CHANNEL_CLOSE)
                        .setMessage(String.valueOf(clientAddr))
                        .setRemoteChannelId(ctx.channel().id().asLongText()).build();
                context.channel().writeAndFlush(dataFrame);
            }
        }

        private void sendData(byte[] bts,String channelIdLongText)
        {
            String channelId = ConnectCache.clientChannel.get(clientUid);
            ChannelHandlerContext context = null;
            if (channelId != null && (context=ConnectCache.channelMap.get(channelId))!=null){

                TrafficCounter trafficCounter = Continer.get(TrafficCounter.class);
                if (trafficCounter != null){
                    if (!trafficCounter.add(this.clientUid,clientAddr,port,Integer.valueOf(bts.length).longValue())) {
                        trafficCounter.sendFlow(context,clientUid,port,clientAddr);
                        return;
                    }
                }

                DataFrameEntity.DataFrame dataFrame = DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.REMOTE_CHANNEL_ACCEPT)
                        .setMessage(String.valueOf(clientAddr))
                        .setData(ByteString.copyFrom(bts))
                        .setRemoteChannelId(channelIdLongText).build();
                context.channel().writeAndFlush(dataFrame);
            }
        }

        @Override
        public void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception {
            map.put(ctx.channel().id().asLongText(),ctx);
            // 发送初始化数据 NULL
            sendData(null,ctx.channel().id().asLongText());
        }

        @Override
        public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
            doClose(ctx);
        }

        @Override
        public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
            if (msg instanceof byte[] bts)
            {
                sendData(bts,ctx.channel().id().asLongText());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            doClose(ctx);
        }
    }


}
