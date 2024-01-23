package xyz.chener.ext.napt.server.core.requestNt;

import com.google.protobuf.ByteString;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.server.core.ConnectCache;
import xyz.chener.ext.napt.server.core.Continer;
import xyz.chener.ext.napt.server.core.TrafficCounter;
import xyz.chener.ext.napt.server.entity.DataFrameCode;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;
import xyz.chener.ext.napt.server.entity.RequestNtType;
import xyz.chener.ext.napt.server.utils.Utils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 对应每个端口的转发服务
 */
@Slf4j
public class RequestNtTcp extends RequestNt {


    // 存放当前端口连接通道   channelId -> channel
    @Getter
    protected final ConcurrentHashMap<String, ChannelHandlerContext> map = new ConcurrentHashMap<>();


    public RequestNtTcp(RequestNtType type, String clientUid, Integer port, String clientAddr, int speedLimit) {
        super(type, clientUid, port, clientAddr);
        if (speedLimit == -1){
            this.speedLimit = Integer.MAX_VALUE;
        }else {
            this.speedLimit = speedLimit;
        }
        isStart = true;
        this.thread = Thread.ofVirtual().name("NtTcp-" + port).start(this::run);
    }



    public void closeOneChannel(String channelId){
        ChannelHandlerContext channelHandlerContext = map.get(channelId);
        if (Objects.nonNull(channelHandlerContext))
            channelHandlerContext.channel().close();
    }

    public void write(String remoteChannelId,byte[] data)
    {
        ChannelHandlerContext channelHandlerContext = map.get(remoteChannelId);
        if (Objects.nonNull(channelHandlerContext))
            channelHandlerContext.channel().writeAndFlush(data);
    }

    private void run()
    {
        while (isStart && !Thread.currentThread().isInterrupted())
        {
            try {
                ServerBootstrap bootstrap = new ServerBootstrap()
                        .group(bossGroup, workGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) throws Exception {
                                speedLimitHandler = new GlobalTrafficShapingHandler(socketChannel.eventLoop(),speedLimit, speedLimit);

                                ChannelPipeline p = socketChannel.pipeline();
                                p.addLast(speedLimitHandler);
                                p.addLast(new ByteArrayEncoder());
                                p.addLast(new ByteArrayDecoder());
                                p.addLast(new TcpSinglePortForwardHandle(clientUid,clientAddr,map,port,type));
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
                if (exception instanceof InterruptedException){
                    Thread.currentThread().interrupt();
                }
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
                                .setTcpRemoteChannelId(channelId).build();
                        context.channel().writeAndFlush(dataFrame);
                    }
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            channel = null;
        }
    }





    @Slf4j
    private static class TcpSinglePortForwardHandle extends ChannelInboundHandlerAdapter {
        private final String clientUid;
        private final String clientAddr;
        private final Map<String, ChannelHandlerContext> map;
        private final int port;
        private final RequestNtType requestNtType;

        private TcpSinglePortForwardHandle(String clientUid, String clientAddr, Map<String, ChannelHandlerContext> map, int port, RequestNtType requestNtType) {
            this.clientUid = clientUid;
            this.clientAddr = clientAddr;
            this.map = map;
            this.port = port;
            this.requestNtType = requestNtType;
        }


        private void doClose(ChannelHandlerContext ctx) {
            map.remove(ctx.channel().id().asLongText());
            String channelId = ConnectCache.clientChannel.get(clientUid);
            ChannelHandlerContext context = null;
            if (channelId != null && (context=ConnectCache.channelMap.get(channelId))!=null){
                DataFrameEntity.DataFrame dataFrame = DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.REMOTE_CHANNEL_CLOSE)
                        .setMessage(String.valueOf(clientAddr))
                        .setTcpRemoteChannelId(ctx.channel().id().asLongText()).build();
                context.channel().writeAndFlush(dataFrame);
            }
        }

        private void sendData(byte[] bts,String channelIdLongText)
        {
            try {
                String channelId = ConnectCache.clientChannel.get(clientUid);
                ChannelHandlerContext context = null;
                if (channelId != null && (context=ConnectCache.channelMap.get(channelId))!=null){

                    TrafficCounter trafficCounter = Continer.get(TrafficCounter.class);
                    if (trafficCounter != null){
                        if (!trafficCounter.add(this.clientUid,clientAddr,port,Integer.valueOf(Optional.ofNullable(bts).orElse(new byte[0]).length).longValue())) {
                            trafficCounter.sendFlow(context,clientUid,port,clientAddr);
                            return;
                        }
                    }

                    DataFrameEntity.DataFrame dataFrame = DataFrameEntity.DataFrame.newBuilder()
                            .setCode(DataFrameCode.REMOTE_CHANNEL_ACCEPT_TCP)
                            .setMessage(String.valueOf(clientAddr))
                            .setData(ByteString.copyFrom(Optional.ofNullable(bts).orElse(new byte[0])))
                            .setTcpRemoteChannelId(channelIdLongText)
                            .setRequestNtType(requestNtType.getCode())
                            .build();
                    context.channel().writeAndFlush(dataFrame);
                }
            }catch (Exception ignored){}
        }


        @Override
        public void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception {
            map.put(ctx.channel().id().asLongText(),ctx);
            // 发送初始化数据 NULL
            sendData(null,ctx.channel().id().asLongText());
        }

        @Override
        public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
            Utils.runIgnoreException(()->{
                doClose(ctx);
                ctx.channel().close();
            });
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
            Utils.runIgnoreException(()->{
                doClose(ctx);
                log.error("OnePortForwardHandle exceptionCaught:{}",cause.getMessage());
                ctx.channel().close();
            });
        }
    }

}
