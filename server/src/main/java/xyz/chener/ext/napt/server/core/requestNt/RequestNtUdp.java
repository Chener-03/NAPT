package xyz.chener.ext.napt.server.core.requestNt;

import com.google.protobuf.ByteString;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import xyz.chener.ext.napt.server.core.ConnectCache;
import xyz.chener.ext.napt.server.entity.DataFrameCode;
import xyz.chener.ext.napt.server.entity.DataFrameEntity;
import xyz.chener.ext.napt.server.entity.RequestNtType;

import java.net.InetSocketAddress;
import java.util.Optional;


@Slf4j
public class RequestNtUdp extends RequestNt {


    public RequestNtUdp(RequestNtType type, String clientUid, Integer port, String clientAddr,int speedLimit) {
        super(type, clientUid, port, clientAddr);
        if (speedLimit == -1){
            this.speedLimit = Integer.MAX_VALUE;
        }else {
            this.speedLimit = speedLimit;
        }
        isStart = true;
        this.thread = Thread.ofVirtual().name("NtUdp-" + port).start(this::run);
    }


    public void write(String ip,int port,byte[] data){
        InetSocketAddress addr = new InetSocketAddress(ip, port);
        if (channel != null && data != null){
            channel.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(data),addr));
        }
    }


    private void run() {
        while (isStart && !Thread.currentThread().isInterrupted()){
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(workGroup)
                        .channel(NioDatagramChannel.class)
                        .option(ChannelOption.SO_BROADCAST, true)
                        .handler(new ChannelInitializer<NioDatagramChannel>(){
                            @Override
                            protected void initChannel(@NotNull NioDatagramChannel ch) throws Exception {
                                speedLimitHandler = new GlobalTrafficShapingHandler(ch.eventLoop(),speedLimit, speedLimit);

                                ChannelPipeline p = ch.pipeline();
                                p.addLast(speedLimitHandler);
                                p.addLast(new ByteArrayEncoder());
                                p.addLast(new ByteArrayDecoder());
                                p.addLast(new UdpSinglePortForwardHandle(clientUid,clientAddr,port,type));
                            }
                        });
                ChannelFuture future = bootstrap.bind(this.port).sync();
                channel = future.channel();
                future.channel().closeFuture().sync();
            }catch (Throwable ex){
                if (ex instanceof InterruptedException){
                    Thread.currentThread().interrupt();
                }
                channel = null;
                if (isStart)
                {
                    log.info("绑定端口异常,即将重试,clientUID:{},port:{},exception:{}",clientUid,port,ex.getMessage());
                    String channelId = ConnectCache.clientChannel.get(clientUid);
                    ChannelHandlerContext context = null;
                    if (channelId != null && (context=ConnectCache.channelMap.get(channelId))!=null){
                        DataFrameEntity.DataFrame dataFrame = DataFrameEntity.DataFrame.newBuilder()
                                .setCode(DataFrameCode.REMOTE_PORT_START_ERROR)
                                .setMessage(String.format("服务器绑定端口异常,即将重试,clientUID:%s,port:%s,exception:%s",clientUid,port,ex.getMessage()))
                                .build();
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
    private static class UdpSinglePortForwardHandle extends ChannelInboundHandlerAdapter {
        private final String clientUid;
        private final String clientAddr;
        private final int port;
        private final RequestNtType requestNtType;


        private UdpSinglePortForwardHandle(String clientUid, String clientAddr , int port, RequestNtType requestNtType) {
            this.clientUid = clientUid;
            this.clientAddr = clientAddr;
            this.port = port;
            this.requestNtType = requestNtType;
        }


        // 收到消息发送给客户端
        private void sendData(byte[] data, InetSocketAddress address){
//            if (this.context != null){
//                this.context.channel().writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(data),address));
//            }

            String channelId = ConnectCache.clientChannel.get(clientUid);
            ChannelHandlerContext context = null;
            if (channelId != null && (context=ConnectCache.channelMap.get(channelId))!=null){

               /* TrafficCounter trafficCounter = Continer.get(TrafficCounter.class);
                if (trafficCounter != null){
                    if (!trafficCounter.add(this.clientUid,clientAddr,port,Integer.valueOf(Optional.ofNullable(data).orElse(new byte[0]).length).longValue())) {
                        trafficCounter.sendFlow(context,clientUid,port,clientAddr);
                        return;
                    }
                }*/

                DataFrameEntity.DataFrame dataFrame = DataFrameEntity.DataFrame.newBuilder()
                        .setCode(DataFrameCode.REMOTE_CHANNEL_ACCEPT_UDP)
                        .setMessage(String.valueOf(clientAddr))
                        .setData(ByteString.copyFrom(Optional.ofNullable(data).orElse(new byte[0])))
                        .setUdpRemoteIp(address.getAddress().getHostAddress())
                        .setUdpRemotePort(address.getPort())
                        .setRequestNtType(requestNtType.getCode())
                        .build();
                context.channel().writeAndFlush(dataFrame);
            }

        }

        private byte[] readAllData(DatagramPacket datagramPacket){
            return datagramPacket.content().array();
        }

        @Override
        public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
            if (msg instanceof DatagramPacket dp){
                sendData(readAllData(dp),dp.sender());
            }
        }

    }

}
